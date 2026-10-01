package com.cloudflow.assistant.client;

import com.cloudflow.assistant.client.AiModels.AnalysisRequest;
import com.cloudflow.assistant.client.AiModels.Answer;
import com.cloudflow.assistant.client.AiModels.GeneratedArtifact;
import com.cloudflow.assistant.client.AiModels.GenerationRequest;
import com.cloudflow.assistant.client.AiModels.IndexRequest;
import com.cloudflow.assistant.client.AiModels.IndexResult;
import com.cloudflow.assistant.client.AiModels.KnowledgeStats;
import com.cloudflow.assistant.client.AiModels.QueryRequest;
import com.cloudflow.assistant.config.AiServiceProperties;
import com.cloudflow.common.exception.ExternalServiceException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for the internal AI service. Failures become {@link ExternalServiceException}s so the
 * platform keeps working when AI is unavailable; only AI features report an error.
 */
@Component
public class AiServiceClient {

  static final String SERVICE = "AI service";

  private final RestClient restClient;
  private final AiServiceProperties properties;

  public AiServiceClient(
      @Qualifier("aiRestClient") RestClient restClient, AiServiceProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  public boolean enabled() {
    return properties.enabled();
  }

  public IndexResult index(IndexRequest request) {
    return call(
        () ->
            restClient
                .post()
                .uri("/v1/index/documents")
                .body(request)
                .retrieve()
                .body(IndexResult.class));
  }

  public KnowledgeStats stats(UUID projectId) {
    return call(
        () ->
            restClient
                .get()
                .uri("/v1/index/projects/{projectId}", projectId)
                .retrieve()
                .body(KnowledgeStats.class));
  }

  public void deleteProject(UUID projectId) {
    call(
        () ->
            restClient
                .delete()
                .uri("/v1/index/projects/{projectId}", projectId)
                .retrieve()
                .toBodilessEntity());
  }

  public Answer query(QueryRequest request) {
    return call(
        () ->
            restClient
                .post()
                .uri("/v1/assistant/query")
                .body(request)
                .retrieve()
                .body(Answer.class));
  }

  public Answer analyzeDeployment(AnalysisRequest request) {
    return call(
        () ->
            restClient
                .post()
                .uri("/v1/analysis/deployment")
                .body(request)
                .retrieve()
                .body(Answer.class));
  }

  public GeneratedArtifact generate(String type, GenerationRequest request) {
    return call(
        () ->
            restClient
                .post()
                .uri("/v1/generate/{type}", type)
                .body(request)
                .retrieve()
                .body(GeneratedArtifact.class));
  }

  private <T> T call(Supplier<T> request) {
    if (!properties.enabled()) {
      throw new ExternalServiceException(
          SERVICE, "AI features are not configured (set CLOUDFLOW_AI_INTERNAL_TOKEN)");
    }
    try {
      T result = request.get();
      if (result == null) {
        throw new ExternalServiceException(SERVICE, "The AI service returned an empty response");
      }
      return result;
    } catch (RestClientResponseException e) {
      String detail = e.getResponseBodyAsString();
      throw new ExternalServiceException(
          SERVICE,
          "The AI service answered "
              + e.getStatusCode().value()
              + (detail.isBlank() ? "" : ": " + detail),
          e);
    } catch (RestClientException e) {
      throw new ExternalServiceException(SERVICE, "The AI service could not be reached", e);
    }
  }
}
