package com.andromedia.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.andromedia.common.EmbeddingConstants;
import org.junit.jupiter.api.Test;

class StubEmbeddingServiceTest {

  private final StubEmbeddingService embeddingService =
      new StubEmbeddingService(EmbeddingConstants.ADA_002_DIMENSIONS);

  @Test
  void producesDeterministicNormalizedVectors() {
    float[] first = embeddingService.embed("validateToken");
    float[] second = embeddingService.embed("validateToken");
    float[] different = embeddingService.embed("refreshToken");

    assertEquals(EmbeddingConstants.ADA_002_DIMENSIONS, first.length);
    assertEquals(EmbeddingConstants.ADA_002_DIMENSIONS, embeddingService.dimensions());
    assertEquals(first.length, second.length);
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i], second[i], 0.0001f);
    }
    assertNotEquals(first[0], different[0], 0.0001f);
  }
}
