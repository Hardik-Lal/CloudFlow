package com.cloudflow.deployment.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.common.web.PageResponse;
import com.cloudflow.deployment.dto.DeploymentLogResponse;
import com.cloudflow.deployment.dto.DeploymentResponse;
import com.cloudflow.deployment.dto.TriggerDeploymentRequest;
import com.cloudflow.deployment.service.DeploymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class DeploymentController {

  private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

  private final DeploymentService deploymentService;

  public DeploymentController(DeploymentService deploymentService) {
    this.deploymentService = deploymentService;
  }

  @PostMapping("/environments/{environmentId}/deployments")
  public ResponseEntity<DeploymentResponse> trigger(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @Valid @RequestBody(required = false) TriggerDeploymentRequest request) {
    DeploymentResponse deployment =
        deploymentService.trigger(
            environmentId, user.id(), request == null ? null : request.commitSha());
    return accepted(deployment);
  }

  @GetMapping("/environments/{environmentId}/deployments")
  public PageResponse<DeploymentResponse> listForEnvironment(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return deploymentService.listForEnvironment(
        environmentId, user.id(), PageRequest.of(page, size, NEWEST_FIRST));
  }

  @GetMapping("/projects/{projectId}/deployments")
  public PageResponse<DeploymentResponse> listForProject(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID projectId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return deploymentService.listForProject(
        projectId, user.id(), PageRequest.of(page, size, NEWEST_FIRST));
  }

  @GetMapping("/deployments/{deploymentId}")
  public DeploymentResponse get(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
    return deploymentService.get(deploymentId, user.id());
  }

  @GetMapping("/deployments/{deploymentId}/logs")
  public List<DeploymentLogResponse> logs(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID deploymentId,
      @RequestParam(defaultValue = "0") @Min(0) long afterId,
      @RequestParam(defaultValue = "500") @Min(1) @Max(1000) int limit) {
    return deploymentService.logs(deploymentId, user.id(), afterId, limit);
  }

  @PostMapping("/deployments/{deploymentId}/rollback")
  public ResponseEntity<DeploymentResponse> rollback(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
    return accepted(deploymentService.rollback(deploymentId, user.id()));
  }

  @PostMapping("/deployments/{deploymentId}/cancel")
  public DeploymentResponse cancel(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
    return deploymentService.cancel(deploymentId, user.id());
  }

  private static ResponseEntity<DeploymentResponse> accepted(DeploymentResponse deployment) {
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/deployments/" + deployment.id()))
        .body(deployment);
  }
}
