package com.cloudflow.deployment.repository;

import com.cloudflow.deployment.domain.DeploymentLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {

  /** Lines after {@code afterId}, oldest first; used for incremental polling. */
  List<DeploymentLog> findAllByDeploymentIdAndIdGreaterThanOrderByIdAsc(
      UUID deploymentId, long afterId, Limit limit);

  List<DeploymentLog> findAllByDeploymentIdOrderByIdDesc(UUID deploymentId, Limit limit);

  List<DeploymentLog> findAllByDeploymentIdOrderByIdAsc(UUID deploymentId);
}
