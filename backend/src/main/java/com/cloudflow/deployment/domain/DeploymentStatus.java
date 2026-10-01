package com.cloudflow.deployment.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Deployment lifecycle: {@code QUEUED → BUILDING → DEPLOYING → HEALTH_CHECK → SUCCEEDED}, or {@code
 * FAILED} / {@code CANCELLED}. A succeeded deployment becomes {@code ROLLED_BACK} when a rollback
 * replaces it.
 */
public enum DeploymentStatus {
  QUEUED,
  BUILDING,
  DEPLOYING,
  HEALTH_CHECK,
  SUCCEEDED,
  FAILED,
  CANCELLED,
  ROLLED_BACK;

  public static final Set<DeploymentStatus> IN_PROGRESS =
      EnumSet.of(QUEUED, BUILDING, DEPLOYING, HEALTH_CHECK);

  public boolean isInProgress() {
    return IN_PROGRESS.contains(this);
  }

  public boolean isCancellable() {
    return this == QUEUED || this == BUILDING;
  }
}
