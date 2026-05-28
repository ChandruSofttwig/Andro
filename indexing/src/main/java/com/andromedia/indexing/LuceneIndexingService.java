package com.andromedia.indexing;

import com.andromedia.common.IndexConstants;
import com.andromedia.common.IndexPathResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
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

  public LuceneIndexingService(
      @Value("${" + IndexConstants.INDEX_PATH_PROPERTY + ":}") String configuredIndexPath) {
    this.configuredIndexPath = configuredIndexPath;
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
          "Dry run — workspace root {} would write index to {}",
          workspaceRoot,
          projectIndexPath);
      processFiles(rootDirectory, request.includedExtensions(), counter, this::logDryRunFile);
    } else {
      log.info("Indexing workspace root {} into {}", workspaceRoot, projectIndexPath);
      try {
        Files.createDirectories(IndexPathResolver.workspaceMarkerPath(workspaceRoot));
        Files.createDirectories(projectIndexPath);
        try (Directory directory = FSDirectory.open(projectIndexPath);
            IndexWriter writer = createWriter(directory, request.recreateIndex())) {
          processFiles(
              rootDirectory,
              request.includedExtensions(),
              counter,
              (filePath, count) -> indexFile(writer, filePath, count));
          writer.commit();
        }
      } catch (IOException ex) {
        throw new IndexingException("Failed to index path: " + rootDirectory, ex);
      }
    }

    Instant finishedAt = Instant.now();
    return new IndexingStats(
        counter.filesVisited, counter.filesIndexed, counter.bytesIndexed, startedAt, finishedAt);
  }

  private void logDryRunFile(Path filePath, Counter counter) {
    try {
      String content = Files.readString(filePath, StandardCharsets.UTF_8);
      counter.filesIndexed++;
      counter.bytesIndexed += content.getBytes(StandardCharsets.UTF_8).length;
      log.info("[dry-run] would index: {}", filePath);
    } catch (IOException ex) {
      throw new IndexingException("Failed to read file: " + filePath, ex);
    }
  }

  private void indexFile(IndexWriter writer, Path filePath, Counter counter) {
    try {
      String extension = extractExtension(filePath);
      String content = Files.readString(filePath, StandardCharsets.UTF_8);
      Document document = toDocument(filePath, extension, content);
      writer.updateDocument(
          new Term(IndexConstants.FIELD_PATH, filePath.toAbsolutePath().normalize().toString()),
          document);
      counter.filesIndexed++;
      counter.bytesIndexed += content.getBytes(StandardCharsets.UTF_8).length;
    } catch (IOException ex) {
      throw new IndexingException("Failed to index file: " + filePath, ex);
    }
  }

  private IndexWriter createWriter(Directory directory, boolean recreateIndex) throws IOException {
    IndexWriterConfig config =
        new IndexWriterConfig(new StandardAnalyzer())
            .setOpenMode(
                recreateIndex
                    ? IndexWriterConfig.OpenMode.CREATE
                    : IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    return new IndexWriter(directory, config);
  }

  @FunctionalInterface
  private interface FileHandler {
    void handle(Path filePath, Counter counter);
  }

  private void processFiles(
      Path rootDirectory, Set<String> includedExtensions, Counter counter, FileHandler handler) {
    try (var paths = Files.walk(rootDirectory)) {
      paths
          .filter(Files::isRegularFile)
          .filter(filePath -> !shouldSkipPath(filePath, rootDirectory))
          .forEach(
              filePath -> {
                counter.filesVisited++;
                if (includedExtensions.contains(extractExtension(filePath))) {
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
    private long bytesIndexed;
  }

  private static Document toDocument(Path filePath, String extension, String content) {
    String absolutePath = filePath.toAbsolutePath().normalize().toString();
    String fileName = filePath.getFileName().toString();
    String language = LanguageResolver.resolve(extension);

    Document document = new Document();
    document.add(new StringField(IndexConstants.FIELD_PATH, absolutePath, Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_FILE_NAME, fileName, Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_EXTENSION, extension, Field.Store.YES));
    document.add(new StringField(IndexConstants.FIELD_LANGUAGE, language, Field.Store.YES));
    document.add(new TextField(IndexConstants.FIELD_CONTENT, content, Field.Store.YES));
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
