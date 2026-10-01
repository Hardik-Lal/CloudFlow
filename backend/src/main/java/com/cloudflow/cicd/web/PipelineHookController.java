package com.cloudflow.cicd.web;

import com.cloudflow.cicd.dto.PipelineDeployRequest;
import com.cloudflow.cicd.dto.PipelineDeploymentStatus;
import com.cloudflow.cicd.service.PipelineHookService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called from GitHub Actions. Authenticated with an environment deploy token in the {@value
 * #TOKEN_HEADER} header (not a user JWT).
 */
@RestController
@RequestMapping("/api/v1/pipeline-hooks")
public class PipelineHookController {

  static final String TOKEN_HEADER = "X-CloudFlow-Deploy-Token";

  private final PipelineHookService hookService;

  public PipelineHookController(PipelineHookService hookService) {
    this.hookService = hookService;
  }

  @PostMapping("/deploy")
  public ResponseEntity<PipelineDeploymentStatus> deploy(
      @RequestHeader(name = TOKEN_HEADER, required = false) String token,
      @Valid @RequestBody PipelineDeployRequest request) {
    PipelineDeploymentStatus deployment = hookService.deploy(token, request);
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/pipeline-hooks/deployments/" + deployment.id()))
        .body(deployment);
  }

  @GetMapping("/deployments/{deploymentId}")
  public PipelineDeploymentStatus status(
      @RequestHeader(name = TOKEN_HEADER, required = false) String token,
      @PathVariable UUID deploymentId) {
    return hookService.status(token, deploymentId);
  }
}
