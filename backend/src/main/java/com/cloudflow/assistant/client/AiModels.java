package com.cloudflow.assistant.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the internal AI service API (camelCase JSON). */
public final class AiModels {

  private AiModels() {}

  public record KnowledgeDocument(
      String sourceType,
      String sourceRef,
      UUID environmentId,
      UUID deploymentId,
      String content,
      Map<String, String> metadata) {}

  public record IndexRequest(
      UUID projectId, List<KnowledgeDocument> documents, boolean replaceAll) {}

  public record IndexResult(int indexed, int unchanged, int removed, int chunks) {}

  public record KnowledgeStats(int documents, int chunks, String lastIndexedAt) {}

  public record Evidence(String source, String reference, String excerpt) {}

  public record Answer(
      String answer,
      String likelyCause,
      List<Evidence> evidence,
      List<String> recommendedActions,
      String confidence,
      String confidenceExplanation) {}

  public record QueryRequest(
      UUID projectId, UUID environmentId, String question, Map<String, Object> liveContext) {}

  public record AnalysisRequest(
      UUID projectId,
      UUID environmentId,
      Map<String, Object> deployment,
      List<String> logs,
      Map<String, Object> configuration,
      Map<String, Object> previousSuccessful,
      String dockerfile) {}

  public record GenerationRequest(
      UUID projectId, UUID environmentId, Map<String, Object> context, String instructions) {}

  public record GeneratedArtifact(String filePath, String content, String explanation) {}
}
