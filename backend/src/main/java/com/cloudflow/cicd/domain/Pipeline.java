package com.cloudflow.cicd.domain;

import com.cloudflow.common.persistence.BaseEntity;
import com.cloudflow.environment.domain.ConfigTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A GitHub Actions workflow that CloudFlow generated for one environment and tracks. */
@Entity
@Table(name = "pipelines")
public class Pipeline extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID projectId;

  @Column(nullable = false, updatable = false)
  private UUID environmentId;

  @Column(nullable = false, updatable = false)
  private String workflowPath;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConfigTemplate template;

  private Long githubWorkflowId;

  @Column(length = 40)
  private String committedSha;

  private UUID createdBy;

  private Instant lastSyncedAt;

  protected Pipeline() {}

  public Pipeline(
      UUID projectId,
      UUID environmentId,
      String workflowPath,
      ConfigTemplate template,
      UUID createdBy) {
    this.projectId = projectId;
    this.environmentId = environmentId;
    this.workflowPath = workflowPath;
    this.template = template;
    this.createdBy = createdBy;
  }

  /** Records a new commit of the workflow file (regeneration keeps the same pipeline). */
  public void recordCommit(String commitSha, ConfigTemplate template, UUID committedBy) {
    this.committedSha = commitSha;
    this.template = template;
    this.createdBy = committedBy;
  }

  public void markSynced(Instant at, Long githubWorkflowId) {
    this.lastSyncedAt = at;
    if (githubWorkflowId != null) {
      this.githubWorkflowId = githubWorkflowId;
    }
  }

  /** File name used by the Actions API to address the workflow. */
  public String workflowFileName() {
    return workflowPath.substring(workflowPath.lastIndexOf('/') + 1);
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public String getWorkflowPath() {
    return workflowPath;
  }

  public ConfigTemplate getTemplate() {
    return template;
  }

  public Long getGithubWorkflowId() {
    return githubWorkflowId;
  }

  public String getCommittedSha() {
    return committedSha;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }
}
