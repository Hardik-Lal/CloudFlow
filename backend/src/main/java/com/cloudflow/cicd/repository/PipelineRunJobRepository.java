package com.cloudflow.cicd.repository;

import com.cloudflow.cicd.domain.PipelineRunJob;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRunJobRepository extends JpaRepository<PipelineRunJob, UUID> {

  Optional<PipelineRunJob> findByGithubJobId(long githubJobId);

  List<PipelineRunJob> findAllByRunIdInOrderByStartedAtAsc(Collection<UUID> runIds);
}
