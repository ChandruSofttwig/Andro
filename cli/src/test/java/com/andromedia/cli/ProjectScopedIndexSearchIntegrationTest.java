package com.andromedia.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andromedia.indexing.IndexingRequest;
import com.andromedia.indexing.LuceneIndexingService;
import com.andromedia.search.LuceneSearchService;
import com.andromedia.search.SearchRequest;
import com.andromedia.search.SearchResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScopedIndexSearchIntegrationTest {

  @TempDir
  Path tempDirectory;

  @Test
  void searchesUseProjectScopedIndexFromNestedDirectory() throws IOException {
    Path baseIndexPath = Files.createDirectories(tempDirectory.resolve("indexes"));
    Path projectA = createWorkspace(tempDirectory.resolve("project-a"), "alphaUniqueToken");
    Path projectB = createWorkspace(tempDirectory.resolve("project-b"), "betaUniqueToken");

    LuceneIndexingService indexingService = new LuceneIndexingService(baseIndexPath.toString());
    indexingService.indexPath(new IndexingRequest(projectA, Set.of("java"), true, false));
    indexingService.indexPath(new IndexingRequest(projectB, Set.of("java"), true, false));

    LuceneSearchService searchService = new LuceneSearchService(baseIndexPath.toString());
    String originalUserDir = System.getProperty("user.dir");
    try {
      Path nestedA = Files.createDirectories(projectA.resolve("module").resolve("src"));
      System.setProperty("user.dir", nestedA.toString());
      SearchResult projectAResult = searchService.search(new SearchRequest("alphaUniqueToken", 10));
      SearchResult crossProjectResult = searchService.search(new SearchRequest("betaUniqueToken", 10));

      assertFalse(projectAResult.hits().isEmpty());
      assertEquals(0, crossProjectResult.hits().size());
      assertTrue(projectAResult.hits().stream().allMatch(hit -> hit.path().startsWith(projectA.toString())));

      Path nestedB = Files.createDirectories(projectB.resolve("feature").resolve("impl"));
      System.setProperty("user.dir", nestedB.toString());
      SearchResult projectBResult = searchService.search(new SearchRequest("betaUniqueToken", 10));

      assertFalse(projectBResult.hits().isEmpty());
      assertTrue(projectBResult.hits().stream().allMatch(hit -> hit.path().startsWith(projectB.toString())));
    } finally {
      System.setProperty("user.dir", originalUserDir);
    }
  }

  private static Path createWorkspace(Path workspaceRoot, String token) throws IOException {
    Path moduleRoot = Files.createDirectories(workspaceRoot.resolve("log-processor-service"));
    Files.createFile(moduleRoot.resolve("pom.xml"));
    Path sourceRoot = Files.createDirectories(moduleRoot.resolve("src"));

    String fileContent =
        """
        package demo;

        class Sample {
          String token = "%s";
        }
        """
            .formatted(token);
    Files.writeString(sourceRoot.resolve("Sample.java"), fileContent);
    return workspaceRoot;
  }

}
