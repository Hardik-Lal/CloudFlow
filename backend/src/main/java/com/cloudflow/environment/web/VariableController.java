package com.cloudflow.environment.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.environment.dto.PutVariableRequest;
import com.cloudflow.environment.dto.VariableResponse;
import com.cloudflow.environment.service.VariableService;
import com.cloudflow.environment.service.VariableService.PutResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/environments/{environmentId}/variables")
public class VariableController {

  private final VariableService variableService;

  public VariableController(VariableService variableService) {
    this.variableService = variableService;
  }

  @GetMapping
  public List<VariableResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    return variableService.list(environmentId, user.id());
  }

  @PutMapping("/{key}")
  public ResponseEntity<VariableResponse> put(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @PathVariable String key,
      @Valid @RequestBody PutVariableRequest request) {
    PutResult result = variableService.put(environmentId, user.id(), key, request);
    if (result.created()) {
      return ResponseEntity.created(
              URI.create("/api/v1/environments/" + environmentId + "/variables/" + key))
          .body(result.variable());
    }
    return ResponseEntity.ok(result.variable());
  }

  @DeleteMapping("/{key}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID environmentId,
      @PathVariable String key) {
    variableService.delete(environmentId, user.id(), key);
    return ResponseEntity.noContent().build();
  }
}
