package com.cloudflow.cicd.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A job (test, build, docker, deploy, health-check) within a pipeline run. */
@Entity
@Table(name = "pipeline_run_jobs")
public class PipelineRunJob extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID runId;

  @Column(nullable = false, unique = true, updatable = false)
  private long githubJobId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(length = 30)
  private String conclusion;

  @Column(length = 1024)
  private String htmlUrl;

  private Instant startedAt;

  private Instant completedAt;

  protected PipelineRunJob() {}

  public PipelineRunJob(UUID runId, long githubJobId) {
    this.runId = runId;
    this.githubJobId = githubJobId;
  }

  public void update(
      String name,
      String status,
      String conclusion,
      String htmlUrl,
      Instant startedAt,
      Instant completedAt) {
    this.name = name;
    this.status = status;
    this.conclusion = conclusion;
    this.htmlUrl = htmlUrl;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
  }

  public UUID getRunId() {
    return runId;
  }

  public long getGithubJobId() {
    return githubJobId;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public String getConclusion() {
    return conclusion;
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
