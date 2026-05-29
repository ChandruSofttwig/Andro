package com.andromedia.search;

import com.andromedia.common.IndexConstants;
import com.andromedia.common.IndexPathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

final class IndexAccess {

  private IndexAccess() {}

  static Path resolveSearchStartPath() {
    String userDirectory = System.getProperty("user.dir", ".");
    return Path.of(userDirectory).toAbsolutePath().normalize();
  }

  static Path resolveProjectIndexPath(String configuredIndexPath) {
    Path searchStartPath = resolveSearchStartPath();
    Path workspaceRoot = IndexPathResolver.resolveWorkspaceRoot(searchStartPath);
    return IndexPathResolver.resolveProjectIndexPath(configuredIndexPath, workspaceRoot);
  }

  static Path resolveWorkspaceRoot() {
    return IndexPathResolver.resolveWorkspaceRoot(resolveSearchStartPath());
  }

  static void validateIndexExists(Path indexDirectoryPath) {
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

  static boolean hasEmbeddingField(Path indexDirectoryPath) {
    try (Directory directory = FSDirectory.open(indexDirectoryPath);
        DirectoryReader reader = DirectoryReader.open(directory)) {
      for (LeafReaderContext leaf : reader.leaves()) {
        if (leaf.reader().getFieldInfos().fieldInfo(IndexConstants.FIELD_EMBEDDING) != null) {
          return true;
        }
      }
      return false;
    } catch (IOException ex) {
      throw new SearchException("Failed to inspect index at: " + indexDirectoryPath, ex);
    }
  }
}
