package com.cloudflow.cicd.dto;

import com.cloudflow.cicd.domain.PipelineRunJob;
import java.time.Instant;

public record PipelineJobResponse(
    String name,
    String status,
    String conclusion,
    String htmlUrl,
    Instant startedAt,
    Instant completedAt) {

  public static PipelineJobResponse from(PipelineRunJob job) {
    return new PipelineJobResponse(
        job.getName(),
        job.getStatus(),
        job.getConclusion(),
        job.getHtmlUrl(),
        job.getStartedAt(),
        job.getCompletedAt());
  }
}
