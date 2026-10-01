package com.cloudflow.monitoring.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.monitoring.dto.EnvironmentEventResponse;
import com.cloudflow.monitoring.dto.EnvironmentMetricsResponse;
import com.cloudflow.monitoring.service.MonitoringService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/environments/{environmentId}")
public class MonitoringController {

  private final MonitoringService monitoringService;

  public MonitoringController(MonitoringService monitoringService) {
    this.monitoringService = monitoringService;
  }

  @GetMapping("/metrics")
  public EnvironmentMetricsResponse metrics(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    return monitoringService.metrics(environmentId, user.id());
  }

  @GetMapping("/events")
  public List<EnvironmentEventResponse> events(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    return monitoringService.events(environmentId, user.id(), limit);
  }
}
