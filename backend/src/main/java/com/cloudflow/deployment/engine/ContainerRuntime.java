package com.cloudflow.deployment.engine;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The deployment target. Docker is the first implementation; the interface keeps the engine
 * independent of it so another target (e.g. Kubernetes) can be added.
 */
public interface ContainerRuntime {

  /**
   * Builds an image from {@code contextDirectory}.
   *
   * @param output receives build output line by line
   * @return the image id
   * @throws RuntimeFailure if the build fails
   */
  String build(Path contextDirectory, Path dockerfile, String imageTag, Consumer<String> output);

  void push(String imageTag, Consumer<String> output);

  boolean imageExists(String imageTag);

  void pull(String imageTag, Consumer<String> output);

  /** Creates and starts a container (creating its network if needed). */
  RunningContainer run(ContainerSpec spec);

  ContainerState inspect(String containerId);

  /** The last {@code lines} lines of the container's stdout and stderr. */
  List<String> tailLogs(String containerId, int lines);

  /** Current resource usage; empty if the container is not running. */
  Optional<ContainerStats> stats(String containerId);

  /**
   * Follows the container's output, starting with the last {@code tail} lines, until the returned
   * handle is closed or the container stops.
   */
  Closeable followLogs(String containerId, int tail, Consumer<String> output);

  /** Stops and removes the container; missing containers are ignored. */
  void remove(String containerId);

  /** A failure reported by the runtime, with a message suitable for deployment logs. */
  class RuntimeFailure extends RuntimeException {

    public RuntimeFailure(String message, Throwable cause) {
      super(message, cause);
    }

    public RuntimeFailure(String message) {
      super(message);
    }
  }
}
