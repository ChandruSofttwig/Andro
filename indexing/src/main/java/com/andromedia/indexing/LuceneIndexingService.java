package com.andromedia.indexing;

import com.andromedia.common.CodeChunk;
import com.andromedia.common.IndexConstants;
import com.andromedia.common.IndexPathResolver;
import com.andromedia.ingestion.ChunkingService;
import com.andromedia.llm.EmbeddingException;
import com.andromedia.llm.EmbeddingService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LuceneIndexingService implements IndexingService {

  private static final Logger log = LoggerFactory.getLogger(LuceneIndexingService.class);

  private static final Set<String> SKIPPED_DIRECTORY_NAMES =
      Set.of(".git", ".svn", ".hg", "target", "build", "node_modules", ".andromedia", ".idea");

  private final String configuredIndexPath;
  private final ChunkingService chunkingService;
  private final EmbeddingService embeddingService;

  public LuceneIndexingService(
      @Value("${" + IndexConstants.INDEX_PATH_PROPERTY + ":}") String configuredIndexPath,
      ChunkingService chunkingService,
      EmbeddingService embeddingService) {
    this.configuredIndexPath = configuredIndexPath;
    this.chunkingService = chunkingService;
    this.embeddingService = embeddingService;
  }

  @Override
  public IndexingStats indexPath(IndexingRequest request) {
    Instant startedAt = Instant.now();
    Counter counter = new Counter();

    Path rootDirectory = request.rootDirectory().toAbsolutePath().normalize();
    Path workspaceRoot = IndexPathResolver.resolveWorkspaceRoot(rootDirectory);
    Path projectIndexPath = IndexPathResolver.resolveProjectIndexPath(configuredIndexPath, rootDirectory);

    if (request.dryRun()) {
      log.info(
          "Dry run — workspace root {} would write index to {}{}",
          workspaceRoot,
          projectIndexPath,
          request.embedChunks() ? " (with embeddings)" : "");
      processFiles(rootDirectory, request, counter, this::logDryRunFile);
    } else {
      log.info(
          "Indexing workspace root {} into {}{}",
          workspaceRoot,
          projectIndexPath,
          request.embedChunks() ? " (with embeddings)" : "");
      try {
        Files.createDirectories(IndexPathResolver.workspaceMarkerPath(workspaceRoot));
        Files.createDirectories(projectIndexPath);
        try (Directory directory = FSDirectory.open(projectIndexPath);
            IndexWriter writer =
                createWriter(directory, request.recreateIndex(), request.embedChunks())) {
          processFiles(
              rootDirectory,
              request,
              counter,
              (filePath, count) -> indexChunksForFile(writer, filePath, request.embedChunks(), count));
          writer.commit();
        }
      } catch (IOException ex) {
        throw new IndexingException("Failed to index path: " + rootDirectory, ex);
      }
    }

    Instant finishedAt = Instant.now();
    return new IndexingStats(
        counter.filesVisited,
        counter.filesIndexed,
        counter.chunksIndexed,
        counter.chunksEmbedded,
        counter.oversizedChunks,
        counter.bytesIndexed,
        startedAt,
        finishedAt);
  }

  private void logDryRunFile(Path filePath, Counter counter) {
    try {
      String extension = extractExtension(filePath);
      String content = Files.readString(filePath, StandardCharsets.UTF_8);
      String language = LanguageResolver.resolve(extension);
      List<CodeChunk> chunks = chunkingService.chunkFile(filePath, content, extension, language);
      counter.filesIndexed++;
      counter.chunksIndexed += chunks.size();
      counter.oversizedChunks += chunks.stream().filter(CodeChunk::oversized).count();
      counter.bytesIndexed += content.getBytes(StandardCharsets.UTF_8).length;
      log.info("[dry-run] would index {} chunks from: {}", chunks.size(), filePath);
    } catch (IOException ex) {
      throw new IndexingException("Failed to read file: " + filePath, ex);
    }
  }

  private void indexChunksForFile(
      IndexWriter writer, Path filePath, boolean embedChunks, Counter counter) {
    try {
      String extension = extractExtension(filePath);
      String content = Files.readString(filePath, StandardCharsets.UTF_8);
      String language = LanguageResolver.resolve(extension);
      List<CodeChunk> chunks = chunkingService.chunkFile(filePath, content, extension, language);

      for (CodeChunk chunk : chunks) {
        if (chunk.oversized()) {
          counter.oversizedChunks++;
          log.warn(
              "Oversized chunk {} ({}:{}-{}, {} lines) — indexed as single unit",
              chunk.chunkId(),
              chunk.fileName(),
              chunk.startLine(),
              chunk.endLine(),
              chunk.endLine() - chunk.startLine() + 1);
        }
        float[] embedding = null;
        if (embedChunks) {
          embedding = embedChunk(chunk);
          counter.chunksEmbedded++;
        }
        Document document = toDocument(chunk, embedding);
        writer.updateDocument(new Term(IndexConstants.FIELD_CHUNK_ID, chunk.chunkId()), document);
        counter.chunksIndexed++;
      }

      counter.filesIndexed++;
      counter.bytesIndexed += content.getBytes(StandardCharsets.UTF_8).length;
    } catch (IOException ex) {
      throw new IndexingException("Failed to index file: " + filePath, ex);
    }
  }

  private float[] embedChunk(CodeChunk chunk) {
    try {
      float[] vector = embeddingService.embed(chunk.content());
      if (vector.length != embeddingService.dimensions()) {
        throw new EmbeddingException(
            "Embedding dimension mismatch for chunk "
                + chunk.chunkId()
                + ": expected "
                + embeddingService.dimensions()
                + " but got "
                + vector.length);
      }
      return vector;
    } catch (EmbeddingException ex) {
      throw new IndexingException("Failed to embed chunk " + chunk.chunkId(), ex);
    }
  }

  private IndexWriter createWriter(Directory directory, boolean recreateIndex, boolean embedChunks)
      throws IOException {
    IndexWriterConfig config =
        new IndexWriterConfig(new StandardAnalyzer())
            .setOpenMode(
                recreateIndex
                    ? IndexWriterConfig.OpenMode.CREATE
                    : IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    if (embedChunks) {
      config.setCodec(new HighDimensionLucene912Codec(embeddingService.dimensions()));
    }
    return new IndexWriter(directory, config);
  }

  @FunctionalInterface
  private interface FileHandler {
    void handle(Path filePath, Counter counter);
  }

  private void processFiles(
      Path rootDirectory, IndexingRequest request, Counter counter, FileHandler handler) {
    try (var paths = Files.walk(rootDirectory)) {
      paths
          .filter(Files::isRegularFile)
          .filter(filePath -> !shouldSkipPath(filePath, rootDirectory))
          .forEach(
              filePath -> {
                counter.filesVisited++;
                if (request.includedExtensions().contains(extractExtension(filePath))) {
                  handler.handle(filePath, counter);
                }
              });
    } catch (IOException ex) {
      throw new IndexingException("Failed to walk directory: " + rootDirectory, ex);
    }
  }

  private static final class Counter {
    private long filesVisited;
    private long filesIndexed;
    private long chunksIndexed;
    private long chunksEmbedded;
    private long oversizedChunks;
    private long bytesIndexed;
  }

  private static Document toDocument(CodeChunk chunk, float[] embedding) {
    Document document = new Document();
    document.add(new StringField(IndexConstants.FIELD_CHUNK_ID, chunk.chunkId(), Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_PATH, chunk.path(), Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_FILE_NAME, chunk.fileName(), Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_EXTENSION, chunk.extension(), Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_LANGUAGE, chunk.language(), Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_UNIT_TYPE, chunk.unitType().name(), Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_START_LINE, Integer.toString(chunk.startLine()), Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_END_LINE, Integer.toString(chunk.endLine()), Field.Store.YES));
    if (chunk.className() != null) {
      document.add(new StringField(IndexConstants.FIELD_CLASS_NAME, chunk.className(), Field.Store.YES));
    }
    if (chunk.methodName() != null) {
      document.add(new StringField(IndexConstants.FIELD_METHOD_NAME, chunk.methodName(), Field.Store.YES));
    }
    document.add(new TextField(IndexConstants.FIELD_CONTENT, chunk.content(), Field.Store.YES));
    if (embedding != null) {
      document.add(
          new KnnFloatVectorField(
              IndexConstants.FIELD_EMBEDDING, embedding, VectorSimilarityFunction.COSINE));
    }
    return document;
  }

  private static boolean shouldSkipPath(Path filePath, Path rootDirectory) {
    Path normalizedFile = filePath.toAbsolutePath().normalize();
    Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
    if (!normalizedFile.startsWith(normalizedRoot)) {
      return true;
    }
    for (Path component : normalizedRoot.relativize(normalizedFile)) {
      if (SKIPPED_DIRECTORY_NAMES.contains(component.toString())) {
        return true;
      }
    }
    return false;
  }

  private static String extractExtension(Path filePath) {
    String fileName = filePath.getFileName().toString();
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(dotIndex + 1).toLowerCase();
  }
}
