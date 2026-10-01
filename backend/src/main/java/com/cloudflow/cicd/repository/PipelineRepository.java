package com.cloudflow.cicd.repository;

import com.cloudflow.cicd.domain.Pipeline;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {

  List<Pipeline> findAllByProjectId(UUID projectId);

  Optional<Pipeline> findByEnvironmentId(UUID environmentId);

  Optional<Pipeline> findFirstByProjectIdInAndWorkflowPath(
      Collection<UUID> projectIds, String workflowPath);
}
