package com.cloudflow.deployment.engine;

import com.cloudflow.environment.domain.DeploymentTarget;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * The engine's single entry point to the deployment targets. Images are always built and pushed
 * with Docker; workloads run on the target chosen for the environment (Docker or Kubernetes), and
 * later calls are routed by the workload id, so an environment can move between targets and the
 * previous workload is still retired correctly.
 */
@Component
public class RoutingContainerRuntime implements ContainerRuntime {

  private final DockerContainerRuntime docker;
  private final List<WorkloadRuntime> runtimes;

  public RoutingContainerRuntime(DockerContainerRuntime docker, List<WorkloadRuntime> runtimes) {
    this.docker = docker;
    this.runtimes = runtimes;
  }

  @Override
  public String build(
      Path contextDirectory, Path dockerfile, String imageTag, Consumer<String> output) {
    return docker.build(contextDirectory, dockerfile, imageTag, output);
  }

  @Override
  public void push(String imageTag, Consumer<String> output) {
    docker.push(imageTag, output);
  }

  @Override
  public boolean imageExists(String imageTag) {
    return docker.imageExists(imageTag);
  }

  @Override
  public void pull(String imageTag, Consumer<String> output) {
    docker.pull(imageTag, output);
  }

  @Override
  public RunningContainer run(ContainerSpec spec) {
    return forTarget(spec.target()).run(spec);
  }

  @Override
  public ContainerState inspect(String containerId) {
    return owner(containerId).inspect(containerId);
  }

  @Override
  public List<String> tailLogs(String containerId, int lines) {
    return owner(containerId).tailLogs(containerId, lines);
  }

  @Override
  public Optional<ContainerStats> stats(String containerId) {
    return owner(containerId).stats(containerId);
  }

  @Override
  public Closeable followLogs(String containerId, int tail, Consumer<String> output) {
    return owner(containerId).followLogs(containerId, tail, output);
  }

  @Override
  public void remove(String containerId) {
    owner(containerId).remove(containerId);
  }

  private WorkloadRuntime forTarget(DeploymentTarget target) {
    return runtimes.stream()
        .filter(runtime -> runtime.target() == target)
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeFailure(
                    target.label() + " is not configured on this CloudFlow installation"));
  }

  private WorkloadRuntime owner(String workloadId) {
    return runtimes.stream()
        .filter(runtime -> runtime.owns(workloadId))
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeFailure(
                    "No configured deployment target manages workload " + workloadId));
  }
}
