package com.cloudflow.cicd.dto;

import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.EnvironmentType;
import java.time.Instant;
import java.util.UUID;

/**
 * @param workflowUrl the workflow file on GitHub
 * @param actionsUrl the workflow's run list on GitHub
 * @param latestRun the most recent run, if any
 */
public record PipelineResponse(
    UUID id,
    UUID projectId,
    UUID environmentId,
    EnvironmentType environmentType,
    String branch,
    String workflowPath,
    ConfigTemplate template,
    String committedSha,
    String workflowUrl,
    String actionsUrl,
    Instant lastSyncedAt,
    PipelineRunResponse latestRun,
    Instant createdAt) {}
