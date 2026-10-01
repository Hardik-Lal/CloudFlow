package com.cloudflow.deployment.service;

import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentLog;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.repository.DeploymentLogRepository;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.storage.domain.ArtifactKind;
import com.cloudflow.storage.service.ArtifactService;
import com.cloudflow.storage.service.ArtifactUpload;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Archives the complete log of every finished deployment to artifact storage. Lines were masked for
 * secrets when they were written, so the archive contains no secret values.
 */
@Component
public class DeploymentLogArchiver {

  private static final Set<DeploymentStatus> FINISHED =
      EnumSet.of(DeploymentStatus.SUCCEEDED, DeploymentStatus.FAILED, DeploymentStatus.CANCELLED);

  private final ArtifactService artifacts;
  private final DeploymentRepository deployments;
  private final DeploymentLogRepository logs;

  public DeploymentLogArchiver(
      ArtifactService artifacts, DeploymentRepository deployments, DeploymentLogRepository logs) {
    this.artifacts = artifacts;
    this.deployments = deployments;
    this.logs = logs;
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void onStatusChanged(DeploymentStatusChangedEvent event) {
    if (!FINISHED.contains(event.status())) {
      return;
    }
    artifacts.storeAsync(
        () -> {
          Deployment deployment = deployments.findById(event.deploymentId()).orElseThrow();
          return ArtifactUpload.text(
              deployment.getProjectId(),
              deployment.getEnvironmentId(),
              deployment.getId(),
              ArtifactKind.ARCHIVED_LOG,
              "deployment-" + deployment.getId() + ".log",
              "text/plain; charset=utf-8",
              render(deployment),
              deployment.getTriggeredBy());
        });
  }

  private String render(Deployment deployment) {
    StringBuilder text =
        new StringBuilder()
            .append("# CloudFlow deployment ")
            .append(deployment.getId())
            .append("\n# status: ")
            .append(deployment.getStatus())
            .append(", target: ")
            .append(deployment.getTarget())
            .append(", commit: ")
            .append(deployment.getCommitSha())
            .append(", image: ")
            .append(deployment.getImageTag())
            .append('\n');
    if (deployment.getFailureReason() != null) {
      text.append("# failure: ").append(deployment.getFailureReason()).append('\n');
    }
    for (DeploymentLog line : logs.findAllByDeploymentIdOrderByIdAsc(deployment.getId())) {
      text.append(line.getLoggedAt())
          .append(' ')
          .append(line.getPhase())
          .append(' ')
          .append(line.getLevel())
          .append(' ')
          .append(line.getMessage())
          .append('\n');
    }
    return text.toString();
  }
}
