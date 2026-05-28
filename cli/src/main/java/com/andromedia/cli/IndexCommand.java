package com.andromedia.cli;

import com.andromedia.common.IndexConstants;
import com.andromedia.indexing.IndexingRequest;
import com.andromedia.indexing.IndexingService;
import com.andromedia.indexing.IndexingStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Command(name = "index", description = "Index source files with Lucene")
public class IndexCommand implements Runnable {

  private final IndexingService indexingService;

  @Parameters(index = "0", description = "Root directory to index")
  private Path rootDirectory;

  @Option(
      names = {"-e", "--ext"},
      split = ",",
      description = "File extensions to include (e.g. java,kt,ts)")
  private Set<String> includedExtensions = new LinkedHashSet<>();

  @Option(names = "--recreate", description = "Recreate index (delete existing)")
  private boolean recreateIndex;

  @Option(
      names = "--dry-run",
      description = "Do not write index, just log what would be indexed")
  private boolean dryRun;

  public IndexCommand(IndexingService indexingService) {
    this.indexingService = indexingService;
  }

  @Override
  public void run() {
    Path validatedRoot = validateRootDirectory(rootDirectory);
    Set<String> extensions = normalizeExtensions(includedExtensions);

    IndexingRequest request =
        new IndexingRequest(validatedRoot, extensions, recreateIndex, dryRun);

    IndexingStats stats = indexingService.indexPath(request);

    System.out.printf(
        "Indexed %d / %d files (%d bytes) from %s in %d ms%n",
        stats.filesIndexed(),
        stats.filesVisited(),
        stats.bytesIndexed(),
        validatedRoot,
        stats.duration().toMillis());
  }

  private static Path validateRootDirectory(Path root) {
    if (root == null) {
      throw new IllegalArgumentException("Root directory is required");
    }
    Path absolute = root.toAbsolutePath().normalize();
    if (!Files.exists(absolute)) {
      throw new IllegalArgumentException("Directory does not exist: " + absolute);
    }
    if (!Files.isDirectory(absolute)) {
      throw new IllegalArgumentException("Path is not a directory: " + absolute);
    }
    return absolute;
  }

  private static Set<String> normalizeExtensions(Set<String> extensions) {
    if (extensions == null || extensions.isEmpty()) {
      return IndexConstants.DEFAULT_EXTENSIONS;
    }
    return extensions.stream()
        .map(IndexCommand::normalizeExtension)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static String normalizeExtension(String extension) {
    String normalized = extension.trim().toLowerCase();
    if (normalized.startsWith(".")) {
      normalized = normalized.substring(1);
    }
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("Extension cannot be empty");
    }
    return normalized;
  }
}
