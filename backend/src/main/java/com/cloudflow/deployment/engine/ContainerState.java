package com.cloudflow.deployment.engine;

import java.time.Instant;

/**
 * @param exists false when the container is gone
 * @param exitCode exit code once stopped, otherwise {@code null}
 * @param startedAt when the container (last) started, if known
 */
public record ContainerState(
    boolean exists,
    boolean running,
    String status,
    Integer exitCode,
    int restartCount,
    Instant startedAt) {

  public static ContainerState missing() {
    return new ContainerState(false, false, "missing", null, 0, null);
  }
}
