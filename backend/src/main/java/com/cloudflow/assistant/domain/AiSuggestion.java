package com.cloudflow.assistant.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An AI-generated artifact awaiting the user's decision. */
@Entity
@Table(name = "ai_suggestions")
public class AiSuggestion extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID projectId;

  @Column(updatable = false)
  private UUID environmentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private SuggestionType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SuggestionStatus status;

  @Column(nullable = false, updatable = false)
  private String filePath;

  @Column(nullable = false, updatable = false)
  private String content;

  @Column(updatable = false)
  private String explanation;

  @Column(updatable = false, length = 2000)
  private String instructions;

  @Column(updatable = false)
  private UUID createdBy;

  private UUID reviewedBy;

  private Instant reviewedAt;

  private String result;

  protected AiSuggestion() {}

  public AiSuggestion(
      UUID projectId,
      UUID environmentId,
      SuggestionType type,
      String filePath,
      String content,
      String explanation,
      String instructions,
      UUID createdBy) {
    this.projectId = projectId;
    this.environmentId = environmentId;
    this.type = type;
    this.status = SuggestionStatus.PENDING;
    this.filePath = filePath;
    this.content = content;
    this.explanation = explanation;
    this.instructions = instructions;
    this.createdBy = createdBy;
  }

  public void markApplied(UUID reviewer, Instant at, String result) {
    this.status = SuggestionStatus.APPLIED;
    review(reviewer, at, result);
  }

  public void markRejected(UUID reviewer, Instant at) {
    this.status = SuggestionStatus.REJECTED;
    review(reviewer, at, null);
  }

  private void review(UUID reviewer, Instant at, String result) {
    this.reviewedBy = reviewer;
    this.reviewedAt = at;
    this.result = result;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public SuggestionType getType() {
    return type;
  }

  public SuggestionStatus getStatus() {
    return status;
  }

  public String getFilePath() {
    return filePath;
  }

  public String getContent() {
    return content;
  }

  public String getExplanation() {
    return explanation;
  }

  public String getInstructions() {
    return instructions;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getResult() {
    return result;
  }
}
