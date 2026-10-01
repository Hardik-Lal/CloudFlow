package com.cloudflow.cicd.dto;

import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.dto.DeploymentResponse;
import java.util.UUID;

/** Minimal deployment view for CI pipelines. */
public record PipelineDeploymentStatus(
    UUID id, DeploymentStatus status, String commitSha, String url, String failureReason) {

  public static PipelineDeploymentStatus from(DeploymentResponse deployment) {
    return new PipelineDeploymentStatus(
        deployment.id(),
        deployment.status(),
        deployment.commitSha(),
        deployment.url(),
        deployment.failureReason());
  }
}
