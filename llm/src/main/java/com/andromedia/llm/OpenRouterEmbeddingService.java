package com.andromedia.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OpenRouterEmbeddingService implements EmbeddingService {

  private static final int MAX_INPUT_CHARS = 30_000;

  private final EmbeddingProperties properties;
  private final RestClient restClient;

  public OpenRouterEmbeddingService(EmbeddingProperties properties) {
    this(properties, RestClient.builder().baseUrl(normalizeBaseUrl(properties.baseUrl())).build());
  }

  OpenRouterEmbeddingService(EmbeddingProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
    if (!properties.hasApiKey()) {
      throw new EmbeddingException(
          "OpenRouter API key is required. Set OPENROUTER_API_KEY or andromedia.embedding.api-key.");
    }
    if (properties.dimensions() <= 0) {
      throw new EmbeddingException("Embedding dimensions must be positive.");
    }
  }

  @Override
  public float[] embed(String text) {
    String input = prepareInput(text);
    EmbeddingsRequest request = new EmbeddingsRequest(properties.model(), input);
    try {
      EmbeddingsResponse response =
          restClient
              .post()
              .uri("/embeddings")
              .headers(this::applyHeaders)
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(EmbeddingsResponse.class);
      return parseEmbedding(response);
    } catch (RestClientException ex) {
      throw new EmbeddingException("OpenRouter embedding request failed", ex);
    }
  }

  @Override
  public int dimensions() {
    return properties.dimensions();
  }

  private void applyHeaders(HttpHeaders headers) {
    headers.setBearerAuth(properties.apiKey());
    if (!properties.httpReferer().isBlank()) {
      headers.set("HTTP-Referer", properties.httpReferer());
    }
    if (!properties.appTitle().isBlank()) {
      headers.set("X-Title", properties.appTitle());
    }
  }

  private static String prepareInput(String text) {
    if (text == null || text.isBlank()) {
      throw new EmbeddingException("Cannot embed empty text");
    }
    if (text.length() <= MAX_INPUT_CHARS) {
      return text;
    }
    return text.substring(0, MAX_INPUT_CHARS);
  }

  private float[] parseEmbedding(EmbeddingsResponse response) {
    if (response == null || response.data() == null || response.data().isEmpty()) {
      throw new EmbeddingException("OpenRouter returned no embedding data");
    }
    List<Double> values = response.data().getFirst().embedding();
    if (values == null || values.isEmpty()) {
      throw new EmbeddingException("OpenRouter returned an empty embedding vector");
    }
    if (values.size() != properties.dimensions()) {
      throw new EmbeddingException(
          "Expected embedding dimension "
              + properties.dimensions()
              + " but received "
              + values.size());
    }
    float[] vector = new float[values.size()];
    for (int i = 0; i < values.size(); i++) {
      vector[i] = values.get(i).floatValue();
    }
    return vector;
  }

  private static String normalizeBaseUrl(String baseUrl) {
    if (baseUrl.endsWith("/")) {
      return baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl;
  }

  private record EmbeddingsRequest(String model, String input) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EmbeddingsResponse(List<EmbeddingData> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EmbeddingData(List<Double> embedding) {}
}
