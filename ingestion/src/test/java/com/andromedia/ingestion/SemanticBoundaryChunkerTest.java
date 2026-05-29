package com.andromedia.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andromedia.common.ChunkUnitType;
import com.andromedia.common.CodeChunk;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticBoundaryChunkerTest {

  private final SemanticBoundaryChunker chunker = new SemanticBoundaryChunker();

  @Test
  void emitsOneChunkPerMethodForJavaSource() {
    String source =
        """
        package demo;

        public class TokenService {
          public void validateToken() {
            String token = "alphaUniqueToken";
          }

          public void refreshToken() {
            String token = "betaUniqueToken";
          }
        }
        """;

    List<CodeChunk> chunks =
        chunker.chunkFile(Path.of("TokenService.java"), source, "java", "java");

    assertEquals(2, chunks.size());
    assertTrue(chunks.stream().allMatch(chunk -> chunk.unitType() == ChunkUnitType.METHOD));
    assertTrue(chunks.stream().anyMatch(chunk -> "validateToken".equals(chunk.methodName())));
    assertTrue(chunks.stream().anyMatch(chunk -> "refreshToken".equals(chunk.methodName())));
    assertTrue(chunks.stream().noneMatch(CodeChunk::oversized));
  }

  @Test
  void doesNotSplitNormalMethodIntoArbitraryFragments() {
    String source =
        """
        package demo;

        public class AuthService {
          public boolean validateJwtToken(String token) {
            if (token == null) {
              return false;
            }
            return token.startsWith("Bearer");
          }
        }
        """;

    List<CodeChunk> chunks =
        chunker.chunkFile(Path.of("AuthService.java"), source, "java", "java");

    assertEquals(1, chunks.size());
    assertEquals("validateJwtToken", chunks.getFirst().methodName());
    assertEquals(ChunkUnitType.METHOD, chunks.getFirst().unitType());
  }

  @Test
  void fallsBackToFileChunkForNonJavaLanguages() {
    String source =
        """
        fun main() {
          println("hello")
        }
        """;

    List<CodeChunk> chunks = chunker.chunkFile(Path.of("App.kt"), source, "kt", "kotlin");

    assertEquals(1, chunks.size());
    assertEquals(ChunkUnitType.FILE, chunks.getFirst().unitType());
    assertFalse(chunks.getFirst().oversized());
  }
}
