package com.cloudflow.assistant.dto;

import com.cloudflow.assistant.domain.AiSuggestion;
import com.cloudflow.assistant.domain.SuggestionStatus;
import com.cloudflow.assistant.domain.SuggestionType;
import java.time.Instant;
import java.util.UUID;

/**
 * @param result what applying did (commit, created variables), once applied
 */
public record SuggestionResponse(
    UUID id,
    UUID projectId,
    UUID environmentId,
    SuggestionType type,
    SuggestionStatus status,
    String filePath,
    String content,
    String explanation,
    String instructions,
    UUID createdBy,
    UUID reviewedBy,
    Instant reviewedAt,
    String result,
    Instant createdAt) {

  public static SuggestionResponse from(AiSuggestion suggestion) {
    return new SuggestionResponse(
        suggestion.getId(),
        suggestion.getProjectId(),
        suggestion.getEnvironmentId(),
        suggestion.getType(),
        suggestion.getStatus(),
        suggestion.getFilePath(),
        suggestion.getContent(),
        suggestion.getExplanation(),
        suggestion.getInstructions(),
        suggestion.getCreatedBy(),
        suggestion.getReviewedBy(),
        suggestion.getReviewedAt(),
        suggestion.getResult(),
        suggestion.getCreatedAt());
  }
}
