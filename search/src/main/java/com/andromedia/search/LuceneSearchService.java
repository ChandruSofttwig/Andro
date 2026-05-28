package com.andromedia.search;

import com.andromedia.common.IndexConstants;
import com.andromedia.common.IndexPathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LuceneSearchService implements SearchService {

  private final String configuredIndexPath;

  public LuceneSearchService(
      @Value("${" + IndexConstants.INDEX_PATH_PROPERTY + ":}") String configuredIndexPath) {
    this.configuredIndexPath = configuredIndexPath;
  }

  @Override
  public SearchResult search(SearchRequest request) {
    Instant startedAt = Instant.now();
    Path searchStartPath = resolveSearchStartPath();
    Path workspaceRoot = IndexPathResolver.resolveWorkspaceRoot(searchStartPath);
    Path projectIndexPath =
        IndexPathResolver.resolveProjectIndexPath(configuredIndexPath, workspaceRoot);
    validateIndexExists(projectIndexPath);

    try (Directory directory = FSDirectory.open(projectIndexPath);
        DirectoryReader reader = DirectoryReader.open(directory)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query query = parseQuery(request.query());
      int maxResults = Math.max(1, request.maxResults());
      TopDocs topDocs = searcher.search(query, maxResults);

      List<SearchHit> hits = new ArrayList<>();
      for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
        var document = searcher.storedFields().document(scoreDoc.doc);
        hits.add(
            new SearchHit(
                document.get(IndexConstants.FIELD_PATH),
                document.get(IndexConstants.FIELD_FILE_NAME),
                scoreDoc.score));
      }

      Duration elapsed = Duration.between(startedAt, Instant.now());
      SearchDiagnostics diagnostics =
          new SearchDiagnostics(
              request.query(), workspaceRoot.toString(), projectIndexPath.toString());
      return new SearchResult(hits, topDocs.totalHits.value, elapsed, diagnostics);
    } catch (IOException ex) {
      throw new SearchException(
          "Failed to search index at: " + projectIndexPath + " (workspace root: " + workspaceRoot + ")",
          ex);
    }
  }

  private static void validateIndexExists(Path indexDirectoryPath) {
    if (!Files.isDirectory(indexDirectoryPath)) {
      throw new SearchException(
          "Index not found at "
              + indexDirectoryPath
              + ". Run `andro index <path>` first.");
    }
    try (Directory directory = FSDirectory.open(indexDirectoryPath)) {
      if (DirectoryReader.indexExists(directory)) {
        return;
      }
    } catch (IOException ex) {
      throw new SearchException("Failed to open index at: " + indexDirectoryPath, ex);
    }
    throw new SearchException(
        "Index not found at "
            + indexDirectoryPath
            + ". Run `andro index <path>` first.");
  }

  private static Query parseQuery(String queryText) {
    QueryParser parser =
        new QueryParser(IndexConstants.FIELD_CONTENT, new StandardAnalyzer());
    parser.setDefaultOperator(QueryParser.Operator.AND);
    try {
      return parser.parse(queryText);
    } catch (ParseException ex) {
      throw new SearchException("Invalid search query: " + queryText, ex);
    }
  }

  private static Path resolveSearchStartPath() {
    String userDirectory = System.getProperty("user.dir", ".");
    return Path.of(userDirectory).toAbsolutePath().normalize();
  }
}
