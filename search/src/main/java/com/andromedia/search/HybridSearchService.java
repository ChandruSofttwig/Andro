package com.andromedia.search;

import com.andromedia.common.IndexConstants;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HybridSearchService {

  private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);
  private static final int CANDIDATE_MULTIPLIER = 5;
  private static final int MIN_CANDIDATE_POOL = 50;

  private final String configuredIndexPath;
  private final LuceneSearchService bm25SearchService;
  private final VectorSearchService vectorSearchService;
  private final Reranker reranker;

  public HybridSearchService(
      @Value("${" + IndexConstants.INDEX_PATH_PROPERTY + ":}") String configuredIndexPath,
      LuceneSearchService bm25SearchService,
      VectorSearchService vectorSearchService,
      Reranker reranker) {
    this.configuredIndexPath = configuredIndexPath;
    this.bm25SearchService = bm25SearchService;
    this.vectorSearchService = vectorSearchService;
    this.reranker = reranker;
  }

  public SearchResult search(SearchRequest request) {
    Instant startedAt = Instant.now();
    Path workspaceRoot = IndexAccess.resolveWorkspaceRoot();
    Path projectIndexPath = IndexAccess.resolveProjectIndexPath(configuredIndexPath);
    IndexAccess.validateIndexExists(projectIndexPath);

    int maxResults = Math.max(1, request.maxResults());
    int candidatePool = Math.max(maxResults * CANDIDATE_MULTIPLIER, MIN_CANDIDATE_POOL);
    SearchRequest candidateRequest =
        new SearchRequest(request.query(), candidatePool, SearchMode.BM25);

    CompletableFuture<List<SearchHit>> bm25Future =
        CompletableFuture.supplyAsync(
            () -> bm25SearchService.search(candidateRequest).hits());
    CompletableFuture<SemanticCandidateResult> semanticFuture =
        CompletableFuture.supplyAsync(() -> loadSemanticCandidates(candidateRequest));

    List<SearchHit> bm25Hits;
    SemanticCandidateResult semanticResult;
    try {
      bm25Hits = bm25Future.get();
      semanticResult = semanticFuture.get();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new SearchException("Hybrid search interrupted", ex);
    } catch (ExecutionException ex) {
      throw new SearchException("Hybrid search failed", ex.getCause());
    }

    List<SearchHit> fused =
        ReciprocalRankFusion.fuse(
            bm25Hits, semanticResult.hits(), maxResults, ReciprocalRankFusion.DEFAULT_K);
    List<SearchHit> reranked = reranker.rerank(request.query(), fused);

    Duration elapsed = Duration.between(startedAt, Instant.now());
    SearchDiagnostics diagnostics =
        new SearchDiagnostics(
            request.query(),
            workspaceRoot.toString(),
            projectIndexPath.toString(),
            bm25Hits.size(),
            semanticResult.hits().size(),
            semanticResult.skipped());
    long totalHits = bm25Hits.size() + semanticResult.hits().size();
    return new SearchResult(reranked, totalHits, elapsed, diagnostics);
  }

  private SemanticCandidateResult loadSemanticCandidates(SearchRequest candidateRequest) {
    Path projectIndexPath = IndexAccess.resolveProjectIndexPath(configuredIndexPath);
    if (!IndexAccess.hasEmbeddingField(projectIndexPath)) {
      log.warn(
          "Hybrid search: no embeddings in index at {} — using BM25 channel only",
          projectIndexPath);
      return new SemanticCandidateResult(List.of(), true);
    }

    SearchRequest semanticRequest =
        new SearchRequest(
            candidateRequest.query(), candidateRequest.maxResults(), SearchMode.SEMANTIC);
    try {
      return new SemanticCandidateResult(vectorSearchService.search(semanticRequest).hits(), false);
    } catch (SearchException ex) {
      log.warn("Hybrid search: semantic channel failed — {}", ex.getMessage());
      return new SemanticCandidateResult(List.of(), true);
    }
  }

  private record SemanticCandidateResult(List<SearchHit> hits, boolean skipped) {}
}
