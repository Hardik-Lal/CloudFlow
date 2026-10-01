package com.cloudflow.deployment.dto;

import jakarta.validation.constraints.Pattern;

/**
 * @param commitSha a specific commit to deploy; defaults to the head of the environment's branch
 */
public record TriggerDeploymentRequest(@Pattern(regexp = "^[0-9a-f]{7,40}$") String commitSha) {}
