package com.cloudflow.organization.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.dto.AuditLogResponse;
import com.cloudflow.audit.repository.AuditLogRepository;
import com.cloudflow.common.web.PageResponse;
import com.cloudflow.organization.domain.Permission;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

  private final AuditLogRepository repository;
  private final OrganizationAccessService organizationAccess;

  public AuditQueryService(
      AuditLogRepository repository, OrganizationAccessService organizationAccess) {
    this.repository = repository;
    this.organizationAccess = organizationAccess;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditLogResponse> search(
      UUID organizationId,
      UUID userId,
      AuditAction action,
      UUID actorId,
      Instant from,
      Instant to,
      Pageable pageable) {
    organizationAccess.requirePermission(organizationId, userId, Permission.AUDIT_READ);
    return PageResponse.of(
        repository.search(
            organizationId,
            action,
            actorId,
            from == null ? Instant.EPOCH : from,
            to == null ? Instant.parse("9999-12-31T00:00:00Z") : to,
            pageable),
        AuditLogResponse::from);
  }
}
