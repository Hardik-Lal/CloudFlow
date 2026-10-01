package com.cloudflow.cicd.service;

import com.cloudflow.cicd.dto.PipelineDeployRequest;
import com.cloudflow.cicd.dto.PipelineDeploymentStatus;
import com.cloudflow.cicd.service.DeployTokenService.DeployTokenPrincipal;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.deployment.dto.DeploymentResponse;
import com.cloudflow.deployment.service.DeploymentService;
import com.cloudflow.environment.dto.EnvironmentRef;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.organization.domain.Permission;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deployment endpoints for CI pipelines, authenticated with deploy tokens instead of user sessions.
 * Every call re-checks that the token's creator may still deploy the environment, so removing
 * someone from the organization also disables the tokens they created.
 */
@Service
public class PipelineHookService {

  private final DeployTokenService deployTokens;
  private final EnvironmentAccessService environmentAccess;
  private final DeploymentService deploymentService;

  public PipelineHookService(
      DeployTokenService deployTokens,
      EnvironmentAccessService environmentAccess,
      DeploymentService deploymentService) {
    this.deployTokens = deployTokens;
    this.environmentAccess = environmentAccess;
    this.deploymentService = deploymentService;
  }

  @Transactional
  public PipelineDeploymentStatus deploy(String rawToken, PipelineDeployRequest request) {
    DeployTokenPrincipal principal = deployTokens.authenticate(rawToken);
    EnvironmentRef environment =
        environmentAccess.require(
            principal.environmentId(),
            principal.actingUserId(),
            Permission.DEPLOYMENT_TRIGGER,
            Permission.DEPLOYMENT_TRIGGER_PRODUCTION);
    DeploymentResponse deployment =
        deploymentService.triggerFromPipeline(
            environment, principal.actingUserId(), request.commitSha(), request.runId());
    return PipelineDeploymentStatus.from(deployment);
  }

  @Transactional
  public PipelineDeploymentStatus status(String rawToken, UUID deploymentId) {
    DeployTokenPrincipal principal = deployTokens.authenticate(rawToken);
    DeploymentResponse deployment = deploymentService.getInternal(deploymentId);
    if (!deployment.environmentId().equals(principal.environmentId())) {
      // Tokens only see deployments of their own environment.
      throw new ResourceNotFoundException("Deployment", deploymentId);
    }
    return PipelineDeploymentStatus.from(deployment);
  }
}
