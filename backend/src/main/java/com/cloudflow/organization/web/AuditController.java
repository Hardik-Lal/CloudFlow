package com.cloudflow.organization.web;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.dto.AuditLogResponse;
import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.common.web.PageResponse;
import com.cloudflow.organization.service.AuditQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class AuditController {

  private final AuditQueryService auditQueryService;

  public AuditController(AuditQueryService auditQueryService) {
    this.auditQueryService = auditQueryService;
  }

  @GetMapping("/api/v1/organizations/{organizationId}/audit-logs")
  public PageResponse<AuditLogResponse> search(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID organizationId,
      @RequestParam(required = false) AuditAction action,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return auditQueryService.search(
        organizationId,
        user.id(),
        action,
        actorId,
        from,
        to,
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
  }
}
