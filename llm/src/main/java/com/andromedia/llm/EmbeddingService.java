package com.andromedia.llm;

public interface EmbeddingService {

  float[] embed(String text);

  int dimensions();
}
