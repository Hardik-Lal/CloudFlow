package com.cloudflow.audit.dto;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.domain.AuditLog;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
    long id,
    AuditAction action,
    UUID actorId,
    String actorUsername,
    String resourceType,
    String resourceId,
    Map<String, String> details,
    String ipAddress,
    Instant createdAt) {

  public static AuditLogResponse from(AuditLog log) {
    return new AuditLogResponse(
        log.getId(),
        log.getAction(),
        log.getActorId(),
        log.getActorUsername(),
        log.getResourceType(),
        log.getResourceId(),
        log.getDetails(),
        log.getIpAddress(),
        log.getCreatedAt());
  }
}
