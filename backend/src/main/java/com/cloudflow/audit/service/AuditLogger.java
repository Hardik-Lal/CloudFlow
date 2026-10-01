package com.cloudflow.audit.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.domain.AuditLog;
import com.cloudflow.audit.repository.AuditLogRepository;
import com.cloudflow.common.security.AuthenticatedUser;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records audit entries. Entries join the caller's transaction, so an action and its audit record
 * commit (or roll back) together. Details must never contain secret values.
 *
 * <p>This module is a leaf: every other module may depend on it, it depends on none of them.
 */
@Service
public class AuditLogger {

  private final AuditLogRepository repository;
  private final Clock clock;

  public AuditLogger(AuditLogRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * @param organizationId the organization the action belongs to ({@code null} for account-level
   *     actions such as signing in)
   * @param actorId the acting user; the username is taken from the current session when it is that
   *     user
   */
  @Transactional
  public void record(
      UUID organizationId,
      UUID actorId,
      AuditAction action,
      String resourceType,
      Object resourceId,
      Map<String, String> details) {
    AuthenticatedUser current = currentUser();
    String username = current != null && current.id().equals(actorId) ? current.username() : null;
    recordWithUsername(
        organizationId, actorId, username, action, resourceType, resourceId, details);
  }

  /** For actions outside an API session (e.g. completing a GitHub sign-in). */
  @Transactional
  public void recordWithUsername(
      UUID organizationId,
      UUID actorId,
      String actorUsername,
      AuditAction action,
      String resourceType,
      Object resourceId,
      Map<String, String> details) {
    repository.save(
        new AuditLog(
            organizationId,
            actorId,
            actorUsername,
            action,
            resourceType,
            resourceId == null ? null : resourceId.toString(),
            details,
            clientIp(),
            clock.instant()));
  }

  private static AuthenticatedUser currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
        ? user
        : null;
  }

  private static String clientIp() {
    return RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes
        ? attributes.getRequest().getRemoteAddr()
        : null;
  }
}
