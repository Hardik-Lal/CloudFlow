package com.cloudflow.environment.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * An environment variable. For secrets, {@code value} holds AES-GCM ciphertext; the plain value is
 * only ever materialized when a deployment injects it into a container.
 */
@Entity
@Table(name = "environment_variables")
public class EnvironmentVariable extends BaseEntity {

  /** Upper-case letters, digits, and underscores, not starting with a digit. */
  public static final String KEY_PATTERN = "^[A-Z_][A-Z0-9_]*$";

  /** Prefix reserved for variables injected by CloudFlow itself. */
  public static final String RESERVED_PREFIX = "CLOUDFLOW_";

  @Column(nullable = false, updatable = false)
  private UUID environmentId;

  @Column(nullable = false, updatable = false, length = 128)
  private String key;

  @Column(nullable = false)
  private String value;

  @Column(nullable = false)
  private boolean secret;

  private UUID updatedBy;

  protected EnvironmentVariable() {}

  public EnvironmentVariable(
      UUID environmentId, String key, String storedValue, boolean secret, UUID updatedBy) {
    this.environmentId = environmentId;
    this.key = key;
    this.value = storedValue;
    this.secret = secret;
    this.updatedBy = updatedBy;
  }

  /**
   * @param storedValue plain text, or ciphertext when {@code secret} is true
   */
  public void replace(String storedValue, boolean secret, UUID updatedBy) {
    this.value = storedValue;
    this.secret = secret;
    this.updatedBy = updatedBy;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public String getKey() {
    return key;
  }

  /** Plain text for regular variables; ciphertext for secrets. */
  public String getStoredValue() {
    return value;
  }

  public boolean isSecret() {
    return secret;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }
}
