package com.cloudflow.cicd.repository;

import com.cloudflow.cicd.domain.PipelineRun;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

  Optional<PipelineRun> findByGithubRunId(long githubRunId);

  Page<PipelineRun> findAllByPipelineId(UUID pipelineId, Pageable pageable);

  /** The newest run of each pipeline. */
  @Query(
      "select r from PipelineRun r where r.pipelineId in :pipelineIds and r.runNumber ="
          + " (select max(o.runNumber) from PipelineRun o where o.pipelineId = r.pipelineId)")
  List<PipelineRun> findLatestRuns(Collection<UUID> pipelineIds);
}
