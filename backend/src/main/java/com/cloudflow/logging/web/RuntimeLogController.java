package com.cloudflow.logging.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.logging.dto.RuntimeLogLine;
import com.cloudflow.logging.service.RuntimeLogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class RuntimeLogController {

  private final RuntimeLogService runtimeLogService;

  public RuntimeLogController(RuntimeLogService runtimeLogService) {
    this.runtimeLogService = runtimeLogService;
  }

  /** The last lines of the running container; live output follows over WebSocket. */
  @GetMapping("/api/v1/environments/{environmentId}/runtime-logs")
  public List<RuntimeLogLine> recent(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @RequestParam(defaultValue = "200") @Min(1) @Max(1000) int tail) {
    return runtimeLogService.recent(environmentId, user.id(), tail);
  }
}
