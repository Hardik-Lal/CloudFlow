package com.cloudflow.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One audited action (append-only: no setters, never updated). */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(updatable = false)
  private UUID organizationId;

  @Column(updatable = false)
  private UUID actorId;

  @Column(updatable = false, length = 100)
  private String actorUsername;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 60)
  private AuditAction action;

  @Column(nullable = false, updatable = false, length = 40)
  private String resourceType;

  @Column(updatable = false, length = 100)
  private String resourceId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private Map<String, String> details;

  @Column(updatable = false, length = 64)
  private String ipAddress;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected AuditLog() {}

  public AuditLog(
      UUID organizationId,
      UUID actorId,
      String actorUsername,
      AuditAction action,
      String resourceType,
      String resourceId,
      Map<String, String> details,
      String ipAddress,
      Instant createdAt) {
    this.organizationId = organizationId;
    this.actorId = actorId;
    this.actorUsername = actorUsername;
    this.action = action;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.details = Map.copyOf(details);
    this.ipAddress = ipAddress;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorUsername() {
    return actorUsername;
  }

  public AuditAction getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public Map<String, String> getDetails() {
    return details;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
