package com.cloudflow.monitoring.dto;

/** Overall state of an environment's running service. */
public enum ServiceStatus {
  /** No live deployment. */
  NOT_DEPLOYED,
  /** Live deployment but no sample yet. */
  UNKNOWN,
  /** Running and passing health checks. */
  UP,
  /** Running but failing health checks. */
  DEGRADED,
  /** The container is not running. */
  DOWN
}
