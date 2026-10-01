package com.cloudflow.deployment.domain;

import com.cloudflow.common.persistence.BaseEntity;
import com.cloudflow.environment.domain.DeploymentTarget;
import com.cloudflow.project.domain.AppType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to deploy an environment. Every step of the pipeline is persisted here, so status and
 * history survive restarts and give the evidence used for troubleshooting.
 */
@Entity
@Table(name = "deployments")
public class Deployment extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID projectId;

  @Column(nullable = false, updatable = false)
  private UUID environmentId;

  @Column(updatable = false)
  private UUID triggeredBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private TriggerType triggerType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DeploymentStatus status;

  @Column(nullable = false, updatable = false)
  private String branch;

  @Column(length = 40)
  private String commitSha;

  @Column(length = 500)
  private String commitMessage;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private AppType appType;

  @Column(length = 500)
  private String imageTag;

  @Column(length = 100)
  private String imageId;

  @Column(length = 100)
  private String containerId;

  @Column(length = 200)
  private String containerName;

  private Integer hostPort;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DeploymentTarget target = DeploymentTarget.DOCKER;

  @Column(updatable = false)
  private UUID rollbackOfId;

  @Column(nullable = false)
  private boolean active;

  private String failureReason;

  private Instant startedAt;

  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private ScanStatus scanStatus;

  private Integer vulnerabilitiesCritical;

  private Integer vulnerabilitiesHigh;

  /** GitHub Actions run id when triggered by a pipeline. */
  private Long pipelineRunId;

  protected Deployment() {}

  private Deployment(
      UUID projectId,
      UUID environmentId,
      UUID triggeredBy,
      TriggerType triggerType,
      String branch,
      String commitSha,
      UUID rollbackOfId) {
    this.projectId = projectId;
    this.environmentId = environmentId;
    this.triggeredBy = triggeredBy;
    this.triggerType = triggerType;
    this.branch = branch;
    this.commitSha = commitSha;
    this.rollbackOfId = rollbackOfId;
    this.status = DeploymentStatus.QUEUED;
  }

  /**
   * @param commitSha a specific commit to deploy, or {@code null} for the branch head
   */
  public static Deployment build(
      UUID projectId,
      UUID environmentId,
      UUID triggeredBy,
      TriggerType triggerType,
      String branch,
      String commitSha) {
    return new Deployment(
        projectId, environmentId, triggeredBy, triggerType, branch, commitSha, null);
  }

  /** A deployment that re-runs the image of an earlier successful deployment. */
  public static Deployment rollbackTo(Deployment target, UUID triggeredBy) {
    Deployment rollback =
        new Deployment(
            target.projectId,
            target.environmentId,
            triggeredBy,
            TriggerType.ROLLBACK,
            target.branch,
            target.commitSha,
            target.getId());
    rollback.commitMessage = target.commitMessage;
    rollback.appType = target.appType;
    rollback.imageTag = target.imageTag;
    rollback.imageId = target.imageId;
    // The image was scanned when it was first built.
    rollback.scanStatus = target.scanStatus;
    rollback.vulnerabilitiesCritical = target.vulnerabilitiesCritical;
    rollback.vulnerabilitiesHigh = target.vulnerabilitiesHigh;
    return rollback;
  }

  public void recordScan(ScanStatus status, Integer critical, Integer high) {
    this.scanStatus = status;
    this.vulnerabilitiesCritical = critical;
    this.vulnerabilitiesHigh = high;
  }

  public void linkPipelineRun(Long githubRunId) {
    this.pipelineRunId = githubRunId;
  }

  public boolean isRollback() {
    return triggerType == TriggerType.ROLLBACK;
  }

  public void start(Instant at) {
    this.startedAt = at;
  }

  public void advanceTo(DeploymentStatus next) {
    this.status = next;
  }

  public void recordSource(String commitSha, String commitMessage, AppType appType) {
    this.commitSha = commitSha;
    this.commitMessage = commitMessage;
    this.appType = appType;
  }

  public void recordImage(String imageTag, String imageId) {
    this.imageTag = imageTag;
    this.imageId = imageId;
  }

  public void recordContainer(
      DeploymentTarget target, String containerId, String containerName, Integer hostPort) {
    this.target = target;
    this.containerId = containerId;
    this.containerName = containerName;
    this.hostPort = hostPort;
  }

  public void succeed(Instant at) {
    this.status = DeploymentStatus.SUCCEEDED;
    this.active = true;
    this.finishedAt = at;
  }

  public void fail(String reason, Instant at) {
    this.status = DeploymentStatus.FAILED;
    this.failureReason = reason;
    this.finishedAt = at;
  }

  public void cancel(Instant at) {
    this.status = DeploymentStatus.CANCELLED;
    this.failureReason = "Cancelled";
    this.finishedAt = at;
  }

  /** Called on the previously serving deployment when a newer one takes over. */
  public void deactivate(boolean replacedByRollback) {
    this.active = false;
    if (replacedByRollback && status == DeploymentStatus.SUCCEEDED) {
      this.status = DeploymentStatus.ROLLED_BACK;
    }
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getTriggeredBy() {
    return triggeredBy;
  }

  public TriggerType getTriggerType() {
    return triggerType;
  }

  public DeploymentStatus getStatus() {
    return status;
  }

  public String getBranch() {
    return branch;
  }

  public String getCommitSha() {
    return commitSha;
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public AppType getAppType() {
    return appType;
  }

  public String getImageTag() {
    return imageTag;
  }

  public String getImageId() {
    return imageId;
  }

  public String getContainerId() {
    return containerId;
  }

  public String getContainerName() {
    return containerName;
  }

  public DeploymentTarget getTarget() {
    return target;
  }

  public Integer getHostPort() {
    return hostPort;
  }

  public UUID getRollbackOfId() {
    return rollbackOfId;
  }

  public boolean isActive() {
    return active;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public ScanStatus getScanStatus() {
    return scanStatus;
  }

  public Integer getVulnerabilitiesCritical() {
    return vulnerabilitiesCritical;
  }

  public Integer getVulnerabilitiesHigh() {
    return vulnerabilitiesHigh;
  }

  public Long getPipelineRunId() {
    return pipelineRunId;
  }
}
