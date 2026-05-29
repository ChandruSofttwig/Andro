package com.andromedia.search;

public record SearchDiagnostics(
    String queryText,
    String workspaceRoot,
    String indexPath,
    Integer bm25Candidates,
    Integer semanticCandidates,
    Boolean semanticSkipped) {

  public SearchDiagnostics(String queryText, String workspaceRoot, String indexPath) {
    this(queryText, workspaceRoot, indexPath, null, null, null);
  }
}
