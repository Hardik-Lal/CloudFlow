package com.cloudflow.cicd.service;

import com.cloudflow.cicd.domain.Pipeline;
import com.cloudflow.cicd.domain.PipelineRun;
import com.cloudflow.cicd.domain.PipelineRun.RunDetails;
import com.cloudflow.cicd.domain.PipelineRunJob;
import com.cloudflow.cicd.repository.PipelineRepository;
import com.cloudflow.cicd.repository.PipelineRunJobRepository;
import com.cloudflow.cicd.repository.PipelineRunRepository;
import com.cloudflow.github.client.GithubModels.WorkflowJob;
import com.cloudflow.github.client.GithubModels.WorkflowRun;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors GitHub Actions runs and jobs into CloudFlow. Used by both the Actions API sync and the
 * webhook receiver, so either source keeps the same records up to date.
 */
@Service
public class PipelineRunSyncService {

  private static final int MAX_COMMIT_MESSAGE = 500;

  private final PipelineRepository pipelineRepository;
  private final PipelineRunRepository runRepository;
  private final PipelineRunJobRepository jobRepository;
  private final Clock clock;

  public PipelineRunSyncService(
      PipelineRepository pipelineRepository,
      PipelineRunRepository runRepository,
      PipelineRunJobRepository jobRepository,
      Clock clock) {
    this.pipelineRepository = pipelineRepository;
    this.runRepository = runRepository;
    this.jobRepository = jobRepository;
    this.clock = clock;
  }

  /**
   * @return the stored run and whether its status changed (so jobs should be refreshed)
   */
  @Transactional
  public UpsertedRun upsertRun(UUID pipelineId, WorkflowRun run) {
    PipelineRun stored =
        runRepository
            .findByGithubRunId(run.id())
            .orElseGet(() -> new PipelineRun(pipelineId, run.id()));
    boolean changed =
        stored.getId() == null
            || !run.status().equals(stored.getStatus())
            || !Objects.equals(run.conclusion(), stored.getConclusion());
    stored.update(
        new RunDetails(
            run.runNumber(),
            Math.max(run.runAttempt(), 1),
            run.event(),
            run.status(),
            run.conclusion(),
            run.headBranch(),
            run.headSha(),
            commitSubject(run),
            run.actor() == null ? null : run.actor().login(),
            run.htmlUrl(),
            run.runStartedAt(),
            run.updatedAt()));
    PipelineRun saved = runRepository.save(stored);
    pipelineRepository
        .findById(pipelineId)
        .ifPresent(pipeline -> pipeline.markSynced(clock.instant(), run.workflowId()));
    return new UpsertedRun(saved.getId(), changed || !saved.isCompleted());
  }

  @Transactional
  public void upsertJob(UUID runId, WorkflowJob job) {
    PipelineRunJob stored =
        jobRepository
            .findByGithubJobId(job.id())
            .orElseGet(() -> new PipelineRunJob(runId, job.id()));
    stored.update(
        job.name(),
        job.status(),
        job.conclusion(),
        job.htmlUrl(),
        job.startedAt(),
        job.completedAt());
    jobRepository.save(stored);
  }

  @Transactional
  public void markSynced(Pipeline pipeline) {
    pipelineRepository
        .findById(pipeline.getId())
        .ifPresent(current -> current.markSynced(clock.instant(), null));
  }

  private static String commitSubject(WorkflowRun run) {
    if (run.headCommit() == null || run.headCommit().message() == null) {
      return null;
    }
    String subject = run.headCommit().message().lines().findFirst().orElse("");
    return subject.length() > MAX_COMMIT_MESSAGE
        ? subject.substring(0, MAX_COMMIT_MESSAGE)
        : subject;
  }

  /**
   * @param refreshJobs whether the run's jobs may have changed
   */
  public record UpsertedRun(UUID runId, boolean refreshJobs) {}
}
