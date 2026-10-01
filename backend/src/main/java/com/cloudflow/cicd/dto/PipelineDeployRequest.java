package com.cloudflow.cicd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * @param runId GitHub Actions run id ({@code $GITHUB_RUN_ID}), recorded on the deployment
 */
public record PipelineDeployRequest(
    @NotBlank @Pattern(regexp = "^[0-9a-f]{40}$") String commitSha, Long runId) {}
