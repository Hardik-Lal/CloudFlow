package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.config.DeploymentProperties;
import com.cloudflow.deployment.engine.ContainerRuntime.RuntimeFailure;
import com.cloudflow.environment.domain.DeploymentTarget;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.InvocationBuilder;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@link ContainerRuntime} backed by the Docker Engine API (via docker-java). */
@Component
public class DockerContainerRuntime implements WorkloadRuntime {

  private static final Logger log = LoggerFactory.getLogger(DockerContainerRuntime.class);
  private static final int STOP_TIMEOUT_SECONDS = 10;
  private static final int RESTART_ATTEMPTS = 3;
  private static final long PIDS_LIMIT = 512;

  private final DockerClient docker;
  private final DeploymentProperties properties;

  public DockerContainerRuntime(DockerClient docker, DeploymentProperties properties) {
    this.docker = docker;
    this.properties = properties;
  }

  public String build(
      Path contextDirectory, Path dockerfile, String imageTag, Consumer<String> output) {
    try {
      return docker
          .buildImageCmd()
          .withBaseDirectory(contextDirectory.toFile())
          .withDockerfile(dockerfile.toFile())
          .withTags(Set.of(imageTag))
          .withLabels(Map.of("cloudflow.managed", "true"))
          .exec(
              new BuildImageResultCallback() {
                @Override
                public void onNext(BuildResponseItem item) {
                  if (item.getStream() != null) {
                    item.getStream().lines().filter(line -> !line.isBlank()).forEach(output);
                  }
                  if (item.getErrorDetail() != null) {
                    output.accept(item.getErrorDetail().getMessage());
                  }
                  super.onNext(item);
                }
              })
          .awaitImageId(properties.buildTimeout().toSeconds(), TimeUnit.SECONDS);
    } catch (DockerClientException | DockerException e) {
      throw new RuntimeFailure("Image build failed: " + rootMessage(e), e);
    }
  }

  public void push(String imageTag, Consumer<String> output) {
    List<String> errors = Collections.synchronizedList(new ArrayList<>());
    try {
      docker
          .pushImageCmd(imageTag)
          .exec(
              new ResultCallback.Adapter<PushResponseItem>() {
                @Override
                public void onNext(PushResponseItem item) {
                  if (item.getErrorDetail() != null) {
                    errors.add(item.getErrorDetail().getMessage());
                  } else if (item.getStatus() != null && item.getProgressDetail() == null) {
                    output.accept(item.getStatus());
                  }
                }
              })
          .awaitCompletion(properties.buildTimeout().toSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeFailure("Image push was interrupted", e);
    } catch (DockerClientException | DockerException e) {
      throw new RuntimeFailure("Image push failed: " + rootMessage(e), e);
    }
    if (!errors.isEmpty()) {
      throw new RuntimeFailure("Image push failed: " + String.join("; ", errors));
    }
  }

  public boolean imageExists(String imageTag) {
    try {
      docker.inspectImageCmd(imageTag).exec();
      return true;
    } catch (NotFoundException e) {
      return false;
    }
  }

  public void pull(String imageTag, Consumer<String> output) {
    try {
      docker
          .pullImageCmd(imageTag)
          .exec(
              new ResultCallback.Adapter<PullResponseItem>() {
                @Override
                public void onNext(PullResponseItem item) {
                  if (item.getStatus() != null && item.getProgressDetail() == null) {
                    output.accept(item.getStatus());
                  }
                }
              })
          .awaitCompletion(properties.buildTimeout().toSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeFailure("Image pull was interrupted", e);
    } catch (DockerClientException | DockerException e) {
      throw new RuntimeFailure("Image pull failed: " + rootMessage(e), e);
    }
  }

  @Override
  public DeploymentTarget target() {
    return DeploymentTarget.DOCKER;
  }

  /** Docker container ids are hexadecimal; Kubernetes workload ids carry a prefix. */
  @Override
  public boolean owns(String workloadId) {
    return workloadId != null && !workloadId.contains(":");
  }

  @Override
  public RunningContainer run(ContainerSpec spec) {
    try {
      ensureNetwork(spec.network());
      ExposedPort exposedPort = ExposedPort.tcp(spec.containerPort());
      Ports portBindings = new Ports();
      // An empty binding lets Docker pick a free host port.
      portBindings.bind(exposedPort, Ports.Binding.empty());

      HostConfig hostConfig =
          HostConfig.newHostConfig()
              .withNetworkMode(spec.network())
              .withPortBindings(portBindings)
              .withBinds(new Bind(spec.volumeName(), new Volume(spec.volumePath())))
              .withRestartPolicy(RestartPolicy.onFailureRestart(RESTART_ATTEMPTS))
              // Hardening: processes cannot gain privileges (setuid binaries), and a fork bomb
              // cannot exhaust the host.
              .withSecurityOpts(List.of("no-new-privileges:true"))
              .withPidsLimit(PIDS_LIMIT);
      if (spec.cpuLimit() != null) {
        hostConfig.withNanoCPUs(spec.cpuLimit().movePointRight(9).longValue());
      }
      if (spec.memoryLimitMb() != null) {
        hostConfig.withMemory(spec.memoryLimitMb() * 1024L * 1024L);
      }

      CreateContainerResponse created =
          docker
              .createContainerCmd(spec.imageTag())
              .withName(spec.name())
              .withEnv(
                  spec.environment().entrySet().stream()
                      .map(entry -> entry.getKey() + "=" + entry.getValue())
                      .toList())
              .withExposedPorts(exposedPort)
              .withLabels(spec.labels())
              .withHostConfig(hostConfig)
              .exec();
      docker.startContainerCmd(created.getId()).exec();
      return new RunningContainer(
          created.getId(), spec.name(), publishedPort(created.getId(), exposedPort));
    } catch (DockerException | DockerClientException e) {
      throw new RuntimeFailure("Container could not be started: " + rootMessage(e), e);
    }
  }

  @Override
  public ContainerState inspect(String containerId) {
    try {
      InspectContainerResponse container = docker.inspectContainerCmd(containerId).exec();
      InspectContainerResponse.ContainerState state = container.getState();
      Long exitCode = state.getExitCodeLong();
      boolean running = Boolean.TRUE.equals(state.getRunning());
      Integer restarts = container.getRestartCount();
      return new ContainerState(
          true,
          running,
          state.getStatus(),
          running || exitCode == null ? null : exitCode.intValue(),
          restarts == null ? 0 : restarts,
          parseInstant(state.getStartedAt()));
    } catch (NotFoundException e) {
      return ContainerState.missing();
    }
  }

  @Override
  public List<String> tailLogs(String containerId, int lines) {
    List<String> output = Collections.synchronizedList(new ArrayList<>());
    try {
      docker
          .logContainerCmd(containerId)
          .withStdOut(true)
          .withStdErr(true)
          .withTail(lines)
          .exec(
              new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                  new String(frame.getPayload(), StandardCharsets.UTF_8)
                      .lines()
                      .forEach(output::add);
                }
              })
          .awaitCompletion(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (NotFoundException e) {
      return List.of();
    }
    return List.copyOf(output);
  }

  @Override
  public Optional<ContainerStats> stats(String containerId) {
    try {
      Statistics statistics =
          docker
              .statsCmd(containerId)
              .withNoStream(true)
              .exec(new InvocationBuilder.AsyncResultCallback<>())
              .awaitResult();
      return Optional.ofNullable(statistics).map(DockerContainerRuntime::toStats);
    } catch (NotFoundException e) {
      return Optional.empty();
    } catch (RuntimeException e) {
      log.debug("Could not read stats for container {}", containerId, e);
      return Optional.empty();
    }
  }

  @Override
  public Closeable followLogs(String containerId, int tail, Consumer<String> output) {
    return docker
        .logContainerCmd(containerId)
        .withStdOut(true)
        .withStdErr(true)
        .withFollowStream(true)
        .withTail(tail)
        .exec(
            new ResultCallback.Adapter<Frame>() {
              @Override
              public void onNext(Frame frame) {
                new String(frame.getPayload(), StandardCharsets.UTF_8).lines().forEach(output);
              }
            });
  }

  /**
   * CPU is computed like {@code docker stats}: the container's share of the host's CPU time between
   * the two samples Docker includes, scaled by the number of online CPUs.
   */
  static ContainerStats toStats(Statistics statistics) {
    double cpuPercent = 0;
    CpuStatsConfig cpu = statistics.getCpuStats();
    CpuStatsConfig previous = statistics.getPreCpuStats();
    if (cpu != null
        && previous != null
        && cpu.getCpuUsage() != null
        && previous.getCpuUsage() != null) {
      long cpuDelta =
          nonNull(cpu.getCpuUsage().getTotalUsage())
              - nonNull(previous.getCpuUsage().getTotalUsage());
      long systemDelta = nonNull(cpu.getSystemCpuUsage()) - nonNull(previous.getSystemCpuUsage());
      long onlineCpus = cpu.getOnlineCpus() == null ? 1 : cpu.getOnlineCpus();
      if (cpuDelta > 0 && systemDelta > 0) {
        cpuPercent = (double) cpuDelta / systemDelta * onlineCpus * 100.0;
      }
    }

    long memoryUsage = 0;
    long memoryLimit = 0;
    MemoryStatsConfig memory = statistics.getMemoryStats();
    if (memory != null) {
      memoryUsage = nonNull(memory.getUsage());
      memoryLimit = nonNull(memory.getLimit());
      if (memory.getStats() != null) {
        // cgroup v2 reports page cache as inactive_file, cgroup v1 as cache.
        Long cache =
            memory.getStats().getInactiveFile() != null
                ? memory.getStats().getInactiveFile()
                : memory.getStats().getCache();
        memoryUsage = Math.max(0, memoryUsage - nonNull(cache));
      }
    }

    long rx = 0;
    long tx = 0;
    if (statistics.getNetworks() != null) {
      for (StatisticNetworksConfig network : statistics.getNetworks().values()) {
        rx += nonNull(network.getRxBytes());
        tx += nonNull(network.getTxBytes());
      }
    }
    return new ContainerStats(cpuPercent, memoryUsage, memoryLimit, rx, tx);
  }

  @Override
  public void remove(String containerId) {
    try {
      docker.stopContainerCmd(containerId).withTimeout(STOP_TIMEOUT_SECONDS).exec();
    } catch (NotFoundException | NotModifiedException e) {
      // Already gone or already stopped.
    }
    try {
      docker.removeContainerCmd(containerId).withForce(true).exec();
    } catch (NotFoundException e) {
      // Already removed.
    }
  }

  private void ensureNetwork(String network) {
    boolean exists =
        docker.listNetworksCmd().withNameFilter(network).exec().stream()
            .anyMatch(existing -> network.equals(existing.getName()));
    if (!exists) {
      log.info("Creating application network {}", network);
      docker.createNetworkCmd().withName(network).withDriver("bridge").exec();
    }
  }

  private Integer publishedPort(String containerId, ExposedPort exposedPort) {
    Map<ExposedPort, Ports.Binding[]> bindings =
        docker
            .inspectContainerCmd(containerId)
            .exec()
            .getNetworkSettings()
            .getPorts()
            .getBindings();
    Ports.Binding[] binding = bindings.get(exposedPort);
    if (binding == null || binding.length == 0 || binding[0].getHostPortSpec() == null) {
      return null;
    }
    return Integer.valueOf(binding[0].getHostPortSpec());
  }

  private static long nonNull(Long value) {
    return value == null ? 0 : value;
  }

  private static Instant parseInstant(String value) {
    if (value == null || value.isBlank() || value.startsWith("0001-")) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String rootMessage(Throwable e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root.getMessage() == null ? e.getMessage() : root.getMessage();
  }
}
