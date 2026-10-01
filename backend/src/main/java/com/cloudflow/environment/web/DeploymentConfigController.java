package com.cloudflow.environment.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.environment.dto.ConfigTemplateResponse;
import com.cloudflow.environment.dto.DeploymentConfigRequest;
import com.cloudflow.environment.dto.DeploymentConfigResponse;
import com.cloudflow.environment.dto.DeploymentTargetResponse;
import com.cloudflow.environment.dto.ValidationResult;
import com.cloudflow.environment.service.DeploymentConfigService;
import com.cloudflow.project.domain.AppType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DeploymentConfigController {

  private final DeploymentConfigService configService;

  public DeploymentConfigController(DeploymentConfigService configService) {
    this.configService = configService;
  }

  @GetMapping("/environments/{environmentId}/config")
  public DeploymentConfigResponse get(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    return configService.get(environmentId, user.id());
  }

  @PutMapping("/environments/{environmentId}/config")
  public DeploymentConfigResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @Valid @RequestBody DeploymentConfigRequest request) {
    return configService.update(environmentId, user.id(), request);
  }

  @PostMapping("/environments/{environmentId}/config/validate")
  public ValidationResult validate(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    return configService.validate(environmentId, user.id());
  }

  @GetMapping("/deployment-targets")
  public List<DeploymentTargetResponse> targets() {
    return configService.targets();
  }

  /** Templates with defaults tailored to an application type (default: unknown). */
  @GetMapping("/config-templates")
  public List<ConfigTemplateResponse> templates(
      @RequestParam(defaultValue = "UNKNOWN") AppType appType) {
    return configService.templates(appType);
  }
}
