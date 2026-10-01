package com.cloudflow.deployment.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.domain.ScanStatus;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.environment.domain.DeploymentTarget;
import com.cloudflow.project.domain.AppType;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactions that persist each step of a running deployment. The runner itself is not
 * transactional, so long builds never hold a database connection.
 */
@Service
public class DeploymentStateService {

  private final DeploymentRepository repository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public DeploymentStateService(
      DeploymentRepository repository, Clock clock, ApplicationEventPublisher events) {
    this.repository = repository;
    this.clock = clock;
    this.events = events;
  }

  /** Moves a queued deployment to BUILDING; empty if it is no longer queued (e.g. cancelled). */
  @Transactional
  public Optional<Deployment> begin(UUID deploymentId) {
    Deployment deployment = load(deploymentId);
    if (deployment.getStatus() != DeploymentStatus.QUEUED) {
      return Optional.empty();
    }
    deployment.start(clock.instant());
    deployment.advanceTo(DeploymentStatus.BUILDING);
    publish(deployment);
    return Optional.of(deployment);
  }

  /**
   * Moves the deployment from {@code expected} to {@code next}.
   *
   * @return false if the status changed concurrently (for example the user cancelled it)
   */
  @Transactional
  public boolean advance(UUID deploymentId, DeploymentStatus expected, DeploymentStatus next) {
    Deployment deployment = load(deploymentId);
    if (deployment.getStatus() != expected) {
      return false;
    }
    deployment.advanceTo(next);
    publish(deployment);
    return true;
  }

  @Transactional
  public void recordSource(UUID deploymentId, String sha, String message, AppType appType) {
    load(deploymentId).recordSource(sha, message, appType);
  }

  @Transactional
  public void recordImage(UUID deploymentId, String imageTag, String imageId) {
    load(deploymentId).recordImage(imageTag, imageId);
  }

  @Transactional
  public void recordScan(UUID deploymentId, ScanStatus status, Integer critical, Integer high) {
    load(deploymentId).recordScan(status, critical, high);
  }

  @Transactional
  public void recordContainer(
      UUID deploymentId,
      DeploymentTarget target,
      String containerId,
      String containerName,
      Integer hostPort) {
    load(deploymentId).recordContainer(target, containerId, containerName, hostPort);
  }

  /**
   * Makes the deployment the environment's serving deployment.
   *
   * @return the container id of the deployment it replaced, if any, so it can be removed
   */
  @Transactional
  public Optional<String> activate(UUID deploymentId) {
    Deployment deployment = load(deploymentId);
    Optional<Deployment> previous =
        repository.findByEnvironmentIdAndActiveTrue(deployment.getEnvironmentId());
    previous.ifPresent(
        current -> {
          current.deactivate(deployment.isRollback());
          // The partial unique index allows one active deployment per environment, so the old
          // one must be deactivated in the database before the new one is activated.
          repository.flush();
          if (current.getStatus() == DeploymentStatus.ROLLED_BACK) {
            publish(current);
          }
        });
    deployment.succeed(clock.instant());
    publish(deployment);
    return previous.map(Deployment::getContainerId);
  }

  @Transactional
  public void fail(UUID deploymentId, String reason) {
    Deployment deployment = load(deploymentId);
    if (deployment.getStatus().isInProgress()) {
      deployment.fail(reason, clock.instant());
      publish(deployment);
    }
  }

  @Transactional(readOnly = true)
  public DeploymentStatus status(UUID deploymentId) {
    return load(deploymentId).getStatus();
  }

  /** Announces the deployment's current status to listeners (after the transaction commits). */
  void publish(Deployment deployment) {
    events.publishEvent(
        new DeploymentStatusChangedEvent(
            deployment.getId(),
            deployment.getEnvironmentId(),
            deployment.getProjectId(),
            deployment.getStatus(),
            deployment.getTriggerType(),
            deployment.getCommitSha(),
            deployment.getFailureReason()));
  }

  private Deployment load(UUID deploymentId) {
    return repository
        .findById(deploymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Deployment", deploymentId));
  }
}
