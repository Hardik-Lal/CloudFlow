package com.cloudflow.cicd.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One GitHub Actions run of a pipeline (workflow metadata mirrored from GitHub). */
@Entity
@Table(name = "pipeline_runs")
public class PipelineRun extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID pipelineId;

  @Column(nullable = false, unique = true, updatable = false)
  private long githubRunId;

  @Column(nullable = false)
  private int runNumber;

  @Column(nullable = false)
  private int runAttempt;

  @Column(nullable = false, length = 50)
  private String event;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(length = 30)
  private String conclusion;

  private String headBranch;

  @Column(nullable = false, length = 40)
  private String headSha;

  @Column(length = 500)
  private String commitMessage;

  @Column(length = 100)
  private String actor;

  @Column(nullable = false, length = 1024)
  private String htmlUrl;

  private Instant startedAt;

  private Instant completedAt;

  protected PipelineRun() {}

  public PipelineRun(UUID pipelineId, long githubRunId) {
    this.pipelineId = pipelineId;
    this.githubRunId = githubRunId;
  }

  /** Copies the latest state reported by GitHub. */
  public void update(RunDetails details) {
    this.runNumber = details.runNumber();
    this.runAttempt = details.runAttempt();
    this.event = details.event();
    this.status = details.status();
    this.conclusion = details.conclusion();
    this.headBranch = details.headBranch();
    this.headSha = details.headSha();
    this.commitMessage = details.commitMessage();
    this.actor = details.actor();
    this.htmlUrl = details.htmlUrl();
    this.startedAt = details.startedAt();
    this.completedAt = "completed".equals(details.status()) ? details.updatedAt() : null;
  }

  public boolean isCompleted() {
    return "completed".equals(status);
  }

  public UUID getPipelineId() {
    return pipelineId;
  }

  public long getGithubRunId() {
    return githubRunId;
  }

  public int getRunNumber() {
    return runNumber;
  }

  public int getRunAttempt() {
    return runAttempt;
  }

  public String getEvent() {
    return event;
  }

  public String getStatus() {
    return status;
  }

  public String getConclusion() {
    return conclusion;
  }

  public String getHeadBranch() {
    return headBranch;
  }

  public String getHeadSha() {
    return headSha;
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public String getActor() {
    return actor;
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

  /** Run attributes as reported by GitHub. */
  public record RunDetails(
      int runNumber,
      int runAttempt,
      String event,
      String status,
      String conclusion,
      String headBranch,
      String headSha,
      String commitMessage,
      String actor,
      String htmlUrl,
      Instant startedAt,
      Instant updatedAt) {}
}
