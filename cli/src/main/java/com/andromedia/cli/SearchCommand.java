package com.andromedia.cli;

import com.andromedia.search.SearchRequest;
import com.andromedia.search.SearchResult;
import com.andromedia.search.SearchService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Command(name = "search", description = "Search the Lucene index")
public class SearchCommand implements Runnable {

  private final SearchService searchService;
  private final CliExecutionContext executionContext;

  @Parameters(index = "0", description = "Search query")
  private String query;

  @Option(names = {"-n", "--limit"}, defaultValue = "10", description = "Maximum results to return")
  private int maxResults;

  public SearchCommand(SearchService searchService, CliExecutionContext executionContext) {
    this.searchService = searchService;
    this.executionContext = executionContext;
  }

  @Override
  public void run() {
    String validatedQuery = validateQuery(query);
    SearchRequest request = new SearchRequest(validatedQuery, maxResults);
    SearchResult result = searchService.search(request);

    if (executionContext.isDebugEnabled() && result.diagnostics() != null) {
      System.err.printf(
          "[debug] query='%s'%n[debug] workspaceRoot=%s%n[debug] indexPath=%s%n[debug] elapsedMs=%d%n",
          result.diagnostics().queryText(),
          result.diagnostics().workspaceRoot(),
          result.diagnostics().indexPath(),
          result.elapsed().toMillis());
    }

    if (result.hits().isEmpty()) {
      System.out.println("No results found.");
    } else {
      for (var hit : result.hits()) {
        System.out.printf(
            "%-40s  (%.2f)  %s%n", hit.fileName(), hit.score(), hit.path());
      }
    }

    System.out.printf(
        "%n%d results (%d total matches) in %d ms%n",
        result.hits().size(), result.totalHits(), result.elapsed().toMillis());
  }

  private static String validateQuery(String queryText) {
    if (queryText == null || queryText.isBlank()) {
      throw new IllegalArgumentException("Search query is required");
    }
    return queryText.trim();
  }
}
