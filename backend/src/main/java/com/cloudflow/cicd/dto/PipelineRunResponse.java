package com.cloudflow.cicd.dto;

import com.cloudflow.cicd.domain.PipelineRun;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param status GitHub run status: queued, in_progress, completed, …
 * @param conclusion set when completed: success, failure, cancelled, …
 */
public record PipelineRunResponse(
    UUID id,
    long githubRunId,
    int runNumber,
    int runAttempt,
    String event,
    String status,
    String conclusion,
    String headBranch,
    String headSha,
    String commitMessage,
    String actor,
    String htmlUrl,
    Instant startedAt,
    Instant completedAt,
    List<PipelineJobResponse> jobs) {

  public static PipelineRunResponse from(PipelineRun run, List<PipelineJobResponse> jobs) {
    return new PipelineRunResponse(
        run.getId(),
        run.getGithubRunId(),
        run.getRunNumber(),
        run.getRunAttempt(),
        run.getEvent(),
        run.getStatus(),
        run.getConclusion(),
        run.getHeadBranch(),
        run.getHeadSha(),
        run.getCommitMessage(),
        run.getActor(),
        run.getHtmlUrl(),
        run.getStartedAt(),
        run.getCompletedAt(),
        jobs);
  }
}
