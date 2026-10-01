package com.cloudflow.deployment.repository;

import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

  Page<Deployment> findAllByEnvironmentId(UUID environmentId, Pageable pageable);

  Page<Deployment> findAllByProjectId(UUID projectId, Pageable pageable);

  Optional<Deployment> findByEnvironmentIdAndActiveTrue(UUID environmentId);

  List<Deployment> findAllByActiveTrue();

  Optional<Deployment> findFirstByEnvironmentIdAndStatusInAndCreatedAtBeforeOrderByCreatedAtDesc(
      UUID environmentId, Collection<DeploymentStatus> statuses, Instant before);

  boolean existsByEnvironmentIdAndStatusIn(
      UUID environmentId, Collection<DeploymentStatus> statuses);

  List<Deployment> findAllByStatusIn(Collection<DeploymentStatus> statuses);
}
