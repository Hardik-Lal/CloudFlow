package com.cloudflow.environment.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.environment.dto.CreateEnvironmentRequest;
import com.cloudflow.environment.dto.EnvironmentResponse;
import com.cloudflow.environment.dto.UpdateEnvironmentRequest;
import com.cloudflow.environment.service.EnvironmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EnvironmentController {

  private final EnvironmentService environmentService;

  public EnvironmentController(EnvironmentService environmentService) {
    this.environmentService = environmentService;
  }

  @GetMapping("/projects/{projectId}/environments")
  public List<EnvironmentResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    return environmentService.list(projectId, user.id());
  }

  @PostMapping("/projects/{projectId}/environments")
  public ResponseEntity<EnvironmentResponse> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateEnvironmentRequest request) {
    EnvironmentResponse environment = environmentService.create(projectId, user.id(), request);
    return ResponseEntity.created(URI.create("/api/v1/environments/" + environment.id()))
        .body(environment);
  }

  @GetMapping("/environments/{environmentId}")
  public EnvironmentResponse get(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    return environmentService.get(environmentId, user.id());
  }

  @PatchMapping("/environments/{environmentId}")
  public EnvironmentResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @Valid @RequestBody UpdateEnvironmentRequest request) {
    return environmentService.update(environmentId, user.id(), request);
  }

  @DeleteMapping("/environments/{environmentId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    environmentService.delete(environmentId, user.id());
    return ResponseEntity.noContent().build();
  }
}
