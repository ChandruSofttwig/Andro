package com.andromedia.search;

import com.andromedia.common.IndexConstants;
import java.io.IOException;
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
    Path workspaceRoot = IndexAccess.resolveWorkspaceRoot();
    Path projectIndexPath = IndexAccess.resolveProjectIndexPath(configuredIndexPath);
    IndexAccess.validateIndexExists(projectIndexPath);

    try (Directory directory = FSDirectory.open(projectIndexPath);
        DirectoryReader reader = DirectoryReader.open(directory)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query query = parseQuery(request.query());
      int maxResults = Math.max(1, request.maxResults());
      TopDocs topDocs = searcher.search(query, maxResults);

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
          "Failed to search index at: " + projectIndexPath + " (workspace root: " + workspaceRoot + ")",
          ex);
    }
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
}
