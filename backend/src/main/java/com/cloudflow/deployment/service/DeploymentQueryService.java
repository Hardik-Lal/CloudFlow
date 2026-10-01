package com.cloudflow.deployment.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentLog;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.dto.ActiveDeployment;
import com.cloudflow.deployment.repository.DeploymentLogRepository;
import com.cloudflow.deployment.repository.DeploymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal, unauthenticated deployment lookups for other modules (monitoring, log streaming).
 * Callers must authorize the user themselves.
 */
@Service
public class DeploymentQueryService {

  private final DeploymentRepository repository;
  private final DeploymentLogRepository logRepository;

  public DeploymentQueryService(
      DeploymentRepository repository, DeploymentLogRepository logRepository) {
    this.repository = repository;
    this.logRepository = logRepository;
  }

  @Transactional(readOnly = true)
  public List<ActiveDeployment> allActive() {
    return repository.findAllByActiveTrue().stream().map(DeploymentQueryService::toActive).toList();
  }

  @Transactional(readOnly = true)
  public Optional<ActiveDeployment> activeFor(UUID environmentId) {
    return repository
        .findByEnvironmentIdAndActiveTrue(environmentId)
        .map(DeploymentQueryService::toActive);
  }

  @Transactional(readOnly = true)
  public UUID environmentOf(UUID deploymentId) {
    return repository
        .findById(deploymentId)
        .map(Deployment::getEnvironmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Deployment", deploymentId));
  }

  /** The most recent successful deployment of the environment created before {@code before}. */
  @Transactional(readOnly = true)
  public Optional<Deployment> previousSuccessful(UUID environmentId, Instant before) {
    return repository.findFirstByEnvironmentIdAndStatusInAndCreatedAtBeforeOrderByCreatedAtDesc(
        environmentId, List.of(DeploymentStatus.SUCCEEDED, DeploymentStatus.ROLLED_BACK), before);
  }

  /** The last {@code limit} log lines of a deployment, oldest first. */
  @Transactional(readOnly = true)
  public List<DeploymentLog> logTail(UUID deploymentId, int limit) {
    List<DeploymentLog> newestFirst =
        logRepository.findAllByDeploymentIdOrderByIdDesc(deploymentId, Limit.of(limit));
    return newestFirst.reversed();
  }

  private static ActiveDeployment toActive(Deployment deployment) {
    return new ActiveDeployment(
        deployment.getId(),
        deployment.getEnvironmentId(),
        deployment.getProjectId(),
        deployment.getTarget(),
        deployment.getContainerId(),
        deployment.getContainerName(),
        deployment.getHostPort());
  }
}
