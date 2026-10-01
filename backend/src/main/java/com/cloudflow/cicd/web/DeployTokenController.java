package com.cloudflow.cicd.web;

import com.cloudflow.cicd.dto.CreateDeployTokenRequest;
import com.cloudflow.cicd.dto.CreatedDeployTokenResponse;
import com.cloudflow.cicd.dto.DeployTokenResponse;
import com.cloudflow.cicd.service.DeployTokenService;
import com.cloudflow.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DeployTokenController {

  private final DeployTokenService deployTokenService;

  public DeployTokenController(DeployTokenService deployTokenService) {
    this.deployTokenService = deployTokenService;
  }

  @GetMapping("/environments/{environmentId}/deploy-tokens")
  public List<DeployTokenResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    return deployTokenService.list(environmentId, user.id());
  }

  /** The raw token is in this response only; it is never retrievable again. */
  @PostMapping("/environments/{environmentId}/deploy-tokens")
  public ResponseEntity<CreatedDeployTokenResponse> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @Valid @RequestBody CreateDeployTokenRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(deployTokenService.create(environmentId, user.id(), request.name()));
  }

  @DeleteMapping("/deploy-tokens/{tokenId}")
  public ResponseEntity<Void> revoke(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tokenId) {
    deployTokenService.revoke(tokenId, user.id());
    return ResponseEntity.noContent().build();
  }
}
