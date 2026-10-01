package com.cloudflow.deployment.engine;

import com.cloudflow.environment.domain.DeploymentTarget;
import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Runs and observes application workloads on one deployment target. Workload ids are opaque to
 * callers; {@link RoutingContainerRuntime} dispatches them to the runtime that created them.
 */
public interface WorkloadRuntime {

  DeploymentTarget target();

  /** Whether {@code workloadId} was created by this runtime. */
  boolean owns(String workloadId);

  RunningContainer run(ContainerSpec spec);

  ContainerState inspect(String workloadId);

  List<String> tailLogs(String workloadId, int lines);

  Optional<ContainerStats> stats(String workloadId);

  Closeable followLogs(String workloadId, int tail, Consumer<String> output);

  void remove(String workloadId);
}
