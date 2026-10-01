package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.config.KubernetesProperties;
import com.cloudflow.deployment.engine.ContainerRuntime.RuntimeFailure;
import com.cloudflow.environment.domain.DeploymentTarget;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs applications on Kubernetes. Each CloudFlow deployment becomes one Deployment (one replica)
 * plus a NodePort Service of the same name, so a new version runs next to the previous one until it
 * is healthy, exactly like the Docker target. The environment's data volume is a
 * PersistentVolumeClaim that survives deployments.
 *
 * <p>Workload ids have the form {@code k8s:<namespace>/<name>}.
 */
public class KubernetesWorkloadRuntime implements WorkloadRuntime {

  static final String ID_PREFIX = "k8s:";
  static final String CONTAINER_NAME = "app";
  private static final String NAME_LABEL = "app.kubernetes.io/name";
  private static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
  private static final Set<String> START_FAILURES =
      Set.of(
          "ErrImagePull",
          "ImagePullBackOff",
          "InvalidImageName",
          "CreateContainerConfigError",
          "CreateContainerError",
          "CrashLoopBackOff");

  private static final Logger log = LoggerFactory.getLogger(KubernetesWorkloadRuntime.class);

  private final KubernetesClient client;
  private final KubernetesProperties properties;
  private final JsonMapper json;

  public KubernetesWorkloadRuntime(
      KubernetesClient client, KubernetesProperties properties, JsonMapper json) {
    this.client = client;
    this.json = json;
    this.properties = properties;
  }

  @Override
  public DeploymentTarget target() {
    return DeploymentTarget.KUBERNETES;
  }

  @Override
  public boolean owns(String workloadId) {
    return workloadId != null && workloadId.startsWith(ID_PREFIX);
  }

  @Override
  public RunningContainer run(ContainerSpec spec) {
    String namespace = properties.namespace();
    String name = kubernetesName(spec.name());
    try {
      ensureNamespace(namespace);
      String claim = ensureVolume(namespace, kubernetesName(spec.volumeName()), spec.labels());
      client
          .apps()
          .deployments()
          .inNamespace(namespace)
          .resource(deployment(name, claim, spec))
          .create();
      Service service =
          client.services().inNamespace(namespace).resource(service(name, spec)).create();
      awaitStarted(namespace, name);
      Integer nodePort = service.getSpec().getPorts().getFirst().getNodePort();
      return new RunningContainer(ID_PREFIX + namespace + "/" + name, name, nodePort);
    } catch (KubernetesClientException e) {
      throw new RuntimeFailure("Kubernetes workload could not be started: " + message(e), e);
    }
  }

  @Override
  public ContainerState inspect(String workloadId) {
    WorkloadRef ref = WorkloadRef.parse(workloadId);
    try {
      Deployment deployment =
          client.apps().deployments().inNamespace(ref.namespace()).withName(ref.name()).get();
      if (deployment == null) {
        return ContainerState.missing();
      }
      Optional<Pod> pod = currentPod(ref);
      if (pod.isEmpty()) {
        return new ContainerState(true, true, "pending", null, 0, null);
      }
      Optional<ContainerStatus> status = appStatus(pod.get());
      int restarts = status.map(ContainerStatus::getRestartCount).orElse(0);
      if (status.isPresent() && status.get().getState().getRunning() != null) {
        return new ContainerState(
            true,
            true,
            "running",
            null,
            restarts,
            parseInstant(status.get().getState().getRunning().getStartedAt()));
      }
      if (status.isPresent() && status.get().getState().getTerminated() != null) {
        return new ContainerState(
            true,
            false,
            "exited",
            status.get().getState().getTerminated().getExitCode(),
            restarts,
            null);
      }
      String waiting =
          status
              .map(s -> s.getState().getWaiting())
              .map(w -> w.getReason())
              .orElse(pod.get().getStatus().getPhase().toLowerCase(Locale.ROOT));
      return new ContainerState(
          true, !START_FAILURES.contains(waiting), waiting, null, restarts, null);
    } catch (KubernetesClientException e) {
      throw new RuntimeFailure("Could not inspect workload: " + message(e), e);
    }
  }

  @Override
  public List<String> tailLogs(String workloadId, int lines) {
    WorkloadRef ref = WorkloadRef.parse(workloadId);
    Optional<Pod> pod = currentPod(ref);
    if (pod.isEmpty()) {
      return List.of();
    }
    try {
      String output =
          client
              .pods()
              .inNamespace(ref.namespace())
              .withName(pod.get().getMetadata().getName())
              .inContainer(CONTAINER_NAME)
              .tailingLines(lines)
              .getLog();
      return output == null ? List.of() : output.lines().toList();
    } catch (KubernetesClientException e) {
      return List.of();
    }
  }

  /**
   * CPU, memory, and network counters from the kubelet summary API of the pod's node (the same
   * source metrics-server uses), so no extra cluster component is required.
   */
  @Override
  public Optional<ContainerStats> stats(String workloadId) {
    WorkloadRef ref = WorkloadRef.parse(workloadId);
    Optional<Pod> pod = currentPod(ref);
    if (pod.isEmpty() || pod.get().getSpec().getNodeName() == null) {
      return Optional.empty();
    }
    String podName = pod.get().getMetadata().getName();
    try {
      String summary =
          client.raw("/api/v1/nodes/" + pod.get().getSpec().getNodeName() + "/proxy/stats/summary");
      if (summary == null) {
        return Optional.empty();
      }
      for (JsonNode podStats : json.readTree(summary).path("pods")) {
        JsonNode podRef = podStats.path("podRef");
        if (podName.equals(podRef.path("name").asString())
            && ref.namespace().equals(podRef.path("namespace").asString())) {
          return Optional.of(toStats(podStats, memoryLimit(pod.get())));
        }
      }
      return Optional.empty();
    } catch (KubernetesClientException | JacksonException e) {
      log.debug("No stats for {}: {}", workloadId, e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public Closeable followLogs(String workloadId, int tail, Consumer<String> output) {
    WorkloadRef ref = WorkloadRef.parse(workloadId);
    Optional<Pod> pod = currentPod(ref);
    if (pod.isEmpty()) {
      return () -> {};
    }
    LogWatch watch =
        client
            .pods()
            .inNamespace(ref.namespace())
            .withName(pod.get().getMetadata().getName())
            .inContainer(CONTAINER_NAME)
            .tailingLines(tail)
            .watchLog();
    Thread reader =
        Thread.ofVirtual()
            .name("k8s-logs-" + ref.name())
            .start(
                () -> {
                  try (BufferedReader lines =
                      new BufferedReader(
                          new InputStreamReader(watch.getOutput(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                      output.accept(line);
                    }
                  } catch (IOException e) {
                    // The stream is closed when the subscriber goes away.
                  }
                });
    return () -> {
      watch.close();
      reader.interrupt();
    };
  }

  @Override
  public void remove(String workloadId) {
    WorkloadRef ref = WorkloadRef.parse(workloadId);
    try {
      client.services().inNamespace(ref.namespace()).withName(ref.name()).delete();
      client.apps().deployments().inNamespace(ref.namespace()).withName(ref.name()).delete();
    } catch (KubernetesClientException e) {
      throw new RuntimeFailure("Could not remove workload: " + message(e), e);
    }
  }

  private Deployment deployment(String name, String claim, ContainerSpec spec) {
    Map<String, String> labels = labels(name, spec.labels());
    ResourceRequirementsBuilder resources = new ResourceRequirementsBuilder();
    if (spec.cpuLimit() != null) {
      resources.addToLimits("cpu", new Quantity(spec.cpuLimit().toPlainString()));
      BigDecimal request = spec.cpuLimit().min(new BigDecimal("0.10"));
      resources.addToRequests("cpu", new Quantity(request.toPlainString()));
    }
    if (spec.memoryLimitMb() != null) {
      Quantity memory = new Quantity(spec.memoryLimitMb() + "Mi");
      resources.addToLimits("memory", memory).addToRequests("memory", memory);
    }
    return new DeploymentBuilder()
        .withNewMetadata()
        .withName(name)
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .withReplicas(1)
        .withNewSelector()
        .addToMatchLabels(NAME_LABEL, name)
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .withAutomountServiceAccountToken(false)
        .addNewContainer()
        .withName(CONTAINER_NAME)
        .withImage(spec.imageTag())
        .withImagePullPolicy("IfNotPresent")
        .withEnv(
            spec.environment().entrySet().stream()
                .map(entry -> new EnvVar(entry.getKey(), entry.getValue(), null))
                .toList())
        .addNewPort()
        .withContainerPort(spec.containerPort())
        .endPort()
        .withResources(resources.build())
        // Same hardening as the Docker target: no privilege escalation.
        .withNewSecurityContext()
        .withAllowPrivilegeEscalation(false)
        .withNewSeccompProfile()
        .withType("RuntimeDefault")
        .endSeccompProfile()
        .endSecurityContext()
        .addNewVolumeMount()
        .withName("data")
        .withMountPath(spec.volumePath())
        .endVolumeMount()
        .endContainer()
        .addNewVolume()
        .withName("data")
        .withNewPersistentVolumeClaim()
        .withClaimName(claim)
        .endPersistentVolumeClaim()
        .endVolume()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

  private Service service(String name, ContainerSpec spec) {
    return new ServiceBuilder()
        .withNewMetadata()
        .withName(name)
        .withLabels(labels(name, spec.labels()))
        .endMetadata()
        .withNewSpec()
        .withType("NodePort")
        .addToSelector(NAME_LABEL, name)
        .addNewPort()
        .withName("http")
        .withPort(spec.containerPort())
        .withTargetPort(new IntOrString(spec.containerPort()))
        .endPort()
        .endSpec()
        .build();
  }

  private void ensureNamespace(String namespace) {
    if (client.namespaces().withName(namespace).get() == null) {
      client
          .namespaces()
          .resource(
              new io.fabric8.kubernetes.api.model.NamespaceBuilder()
                  .withNewMetadata()
                  .withName(namespace)
                  .addToLabels(MANAGED_BY_LABEL, "cloudflow")
                  .endMetadata()
                  .build())
          .serverSideApply();
    }
  }

  private String ensureVolume(String namespace, String claimName, Map<String, String> specLabels) {
    if (client.persistentVolumeClaims().inNamespace(namespace).withName(claimName).get() != null) {
      return claimName;
    }
    PersistentVolumeClaimBuilder claim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(claimName)
            .addToLabels(MANAGED_BY_LABEL, "cloudflow")
            .addToLabels(
                "cloudflow.environment", specLabels.getOrDefault("cloudflow.environment", ""))
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .addToRequests("storage", new Quantity(properties.volumeSize()))
            .endResources()
            .endSpec();
    if (!properties.storageClass().isBlank()) {
      claim.editSpec().withStorageClassName(properties.storageClass()).endSpec();
    }
    PersistentVolumeClaim created =
        client.persistentVolumeClaims().inNamespace(namespace).resource(claim.build()).create();
    return created.getMetadata().getName();
  }

  /** Waits until the pod's container has started, or fails on errors that will not resolve. */
  private void awaitStarted(String namespace, String name) {
    WorkloadRef ref = new WorkloadRef(namespace, name);
    Instant deadline = Instant.now().plus(properties.startTimeout());
    while (Instant.now().isBefore(deadline)) {
      Optional<Pod> pod = currentPod(ref);
      Optional<ContainerStatus> status = pod.flatMap(KubernetesWorkloadRuntime::appStatus);
      if (status.isPresent()) {
        if (status.get().getState().getRunning() != null
            || status.get().getState().getTerminated() != null) {
          return;
        }
        var waiting = status.get().getState().getWaiting();
        if (waiting != null && START_FAILURES.contains(waiting.getReason())) {
          throw new RuntimeFailure(
              "The pod could not start: "
                  + waiting.getReason()
                  + (waiting.getMessage() == null ? "" : " (" + waiting.getMessage() + ")"));
        }
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeFailure("Interrupted while waiting for the pod to start");
      }
    }
    throw new RuntimeFailure(
        "The pod did not start within " + properties.startTimeout().toSeconds() + "s");
  }

  private Optional<Pod> currentPod(WorkloadRef ref) {
    try {
      return client
          .pods()
          .inNamespace(ref.namespace())
          .withLabel(NAME_LABEL, ref.name())
          .list()
          .getItems()
          .stream()
          .filter(pod -> pod.getMetadata().getDeletionTimestamp() == null)
          .max(Comparator.comparing(pod -> pod.getMetadata().getCreationTimestamp()));
    } catch (KubernetesClientException e) {
      return Optional.empty();
    }
  }

  private static Optional<ContainerStatus> appStatus(Pod pod) {
    if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
      return Optional.empty();
    }
    return pod.getStatus().getContainerStatuses().stream()
        .filter(status -> CONTAINER_NAME.equals(status.getName()))
        .findFirst();
  }

  private static long memoryLimit(Pod pod) {
    return pod.getSpec().getContainers().stream()
        .filter(container -> CONTAINER_NAME.equals(container.getName()))
        .findFirst()
        .map(container -> container.getResources().getLimits())
        .map(limits -> limits.get("memory"))
        .map(quantity -> Quantity.getAmountInBytes(quantity).longValue())
        .orElse(0L);
  }

  private static ContainerStats toStats(JsonNode podStats, long memoryLimit) {
    JsonNode container = null;
    for (JsonNode candidate : podStats.path("containers")) {
      if (CONTAINER_NAME.equals(candidate.path("name").asString())) {
        container = candidate;
      }
    }
    JsonNode source = container == null ? podStats : container;
    // usageNanoCores: nanocores in use; 1e9 = one full core = 100 %.
    double cpuPercent = source.path("cpu").path("usageNanoCores").asDouble(0) / 10_000_000d;
    long memory = source.path("memory").path("workingSetBytes").asLong(0);
    JsonNode network = podStats.path("network");
    return new ContainerStats(
        cpuPercent,
        memory,
        memoryLimit,
        network.path("rxBytes").asLong(0),
        network.path("txBytes").asLong(0));
  }

  private static Map<String, String> labels(String name, Map<String, String> specLabels) {
    Map<String, String> labels = new HashMap<>();
    specLabels.forEach(
        (key, value) -> labels.put(key.replace("cloudflow.", "cloudflow.io/"), value));
    labels.put(NAME_LABEL, name);
    labels.put(MANAGED_BY_LABEL, "cloudflow");
    return labels;
  }

  /** Kubernetes names: lower-case DNS labels of at most 63 characters. */
  static String kubernetesName(String name) {
    String cleaned =
        name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
    if (cleaned.length() > 63) {
      cleaned = cleaned.substring(0, 63);
    }
    return cleaned.replaceAll("^-+|-+$", "");
  }

  private static Instant parseInstant(String value) {
    try {
      return value == null ? null : Instant.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String message(KubernetesClientException e) {
    return e.getStatus() != null && e.getStatus().getMessage() != null
        ? e.getStatus().getMessage()
        : e.getMessage();
  }

  record WorkloadRef(String namespace, String name) {
    static WorkloadRef parse(String workloadId) {
      if (workloadId == null || !workloadId.startsWith(ID_PREFIX)) {
        throw new IllegalArgumentException("Not a Kubernetes workload id: " + workloadId);
      }
      String[] parts = workloadId.substring(ID_PREFIX.length()).split("/", 2);
      if (parts.length != 2) {
        throw new IllegalArgumentException("Not a Kubernetes workload id: " + workloadId);
      }
      return new WorkloadRef(parts[0], parts[1]);
    }
  }
}
