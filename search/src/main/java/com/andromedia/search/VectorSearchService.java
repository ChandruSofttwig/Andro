package com.andromedia.search;

import com.andromedia.common.IndexConstants;
import com.andromedia.llm.EmbeddingService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VectorSearchService {

  private final String configuredIndexPath;
  private final EmbeddingService embeddingService;

  public VectorSearchService(
      @Value("${" + IndexConstants.INDEX_PATH_PROPERTY + ":}") String configuredIndexPath,
      EmbeddingService embeddingService) {
    this.configuredIndexPath = configuredIndexPath;
    this.embeddingService = embeddingService;
  }

  public SearchResult search(SearchRequest request) {
    Instant startedAt = Instant.now();
    Path workspaceRoot = IndexAccess.resolveWorkspaceRoot();
    Path projectIndexPath = IndexAccess.resolveProjectIndexPath(configuredIndexPath);
    IndexAccess.validateIndexExists(projectIndexPath);
    if (!IndexAccess.hasEmbeddingField(projectIndexPath)) {
      throw new SearchException(
          "No embeddings in index at "
              + projectIndexPath
              + ". Re-index with `andro index --embed <path>`.");
    }

    float[] queryVector = embeddingService.embed(request.query());
    int maxResults = Math.max(1, request.maxResults());

    try (Directory directory = FSDirectory.open(projectIndexPath);
        DirectoryReader reader = DirectoryReader.open(directory)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      KnnFloatVectorQuery vectorQuery =
          new KnnFloatVectorQuery(IndexConstants.FIELD_EMBEDDING, queryVector, maxResults);
      TopDocs topDocs = searcher.search(vectorQuery, maxResults);

      List<SearchHit> hits = new ArrayList<>();
      for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
        var document = searcher.storedFields().document(scoreDoc.doc);
        hits.add(IndexDocumentMapper.toSearchHit(document, scoreDoc.score));
      }

      Duration elapsed = Duration.between(startedAt, Instant.now());
      SearchDiagnostics diagnostics =
          new SearchDiagnostics(
              request.query(), workspaceRoot.toString(), projectIndexPath.toString());
      return new SearchResult(hits, topDocs.totalHits.value, elapsed, diagnostics);
    } catch (IOException ex) {
      throw new SearchException(
          "Failed to run semantic search at: "
              + projectIndexPath
              + " (workspace root: "
              + workspaceRoot
              + ")",
          ex);
    }
  }
}
