package com.cloudflow.deployment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudflow.deployment.config.KubernetesProperties;
import com.cloudflow.deployment.engine.ContainerRuntime.RuntimeFailure;
import com.cloudflow.environment.domain.DeploymentTarget;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.Closeable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** The Kubernetes target against a real single-node cluster (k3s). */
@Testcontainers
class KubernetesWorkloadRuntimeTest {

  /** A tiny HTTP server that listens on WHOAMI_PORT_NUMBER and answers every path. */
  private static final String APP_IMAGE = "traefik/whoami:v1.11.0";

  @Container
  static final K3sContainer K3S =
      new K3sContainer(DockerImageName.parse("rancher/k3s:v1.37.0-k3s1"));

  private static KubernetesClient client;
  private static KubernetesWorkloadRuntime runtime;

  @BeforeAll
  static void connect() {
    client =
        new KubernetesClientBuilder()
            .withConfig(Config.fromKubeconfig(K3S.getKubeConfigYaml()))
            .build();
    runtime =
        new KubernetesWorkloadRuntime(
            client,
            new KubernetesProperties(
                true,
                "",
                "",
                "cloudflow-apps",
                "localhost",
                "localhost",
                "",
                "64Mi",
                Duration.ofMinutes(3)),
            JsonMapper.builder().build());
  }

  @AfterAll
  static void close() {
    client.close();
  }

  @Test
  void runsObservesAndRemovesAWorkload() throws Exception {
    RunningContainer container = runtime.run(spec("cf-demo-development-1a2b3c4d", APP_IMAGE));

    assertThat(container.id()).isEqualTo("k8s:cloudflow-apps/cf-demo-development-1a2b3c4d");
    assertThat(runtime.owns(container.id())).isTrue();
    assertThat(container.hostPort()).isBetween(30000, 32767);

    ContainerState state = runtime.inspect(container.id());
    assertThat(state.exists()).isTrue();
    assertThat(state.running()).isTrue();
    assertThat(state.restartCount()).isZero();

    // The NodePort service routes to the pod.
    String response =
        await(
            () -> {
              try {
                return Optional.ofNullable(
                        client.raw(
                            "/api/v1/namespaces/cloudflow-apps/services/"
                                + container.name()
                                + ":http/proxy/"))
                    .filter(body -> body.contains("Hostname"));
              } catch (RuntimeException e) {
                return Optional.empty();
              }
            });
    assertThat(response).contains("GET / HTTP");

    assertThat(await(() -> nonEmpty(runtime.tailLogs(container.id(), 20))))
        .anyMatch(line -> line.contains("8080"));

    ContainerStats stats = await(() -> runtime.stats(container.id()));
    assertThat(stats.memoryUsageBytes()).isPositive();
    assertThat(stats.memoryLimitBytes()).isEqualTo(128L * 1024 * 1024);

    List<String> followed = new CopyOnWriteArrayList<>();
    Closeable follow = runtime.followLogs(container.id(), 5, followed::add);
    try {
      await(() -> nonEmpty(followed));
    } finally {
      follow.close();
    }

    // The environment's data volume is a claim that outlives the workload.
    assertThat(
            client
                .persistentVolumeClaims()
                .inNamespace("cloudflow-apps")
                .withName("cf-data-demo")
                .get())
        .isNotNull();

    runtime.remove(container.id());
    await(() -> runtime.inspect(container.id()).exists() ? Optional.empty() : Optional.of(true));
    assertThat(runtime.inspect(container.id()).exists()).isFalse();
  }

  @Test
  void failsFastWhenTheImageCannotBePulled() {
    assertThatThrownBy(
            () ->
                runtime.run(
                    spec("cf-demo-staging-5e6f7a8b", "localhost:1/cloudflow/missing:latest")))
        .isInstanceOf(RuntimeFailure.class)
        .hasMessageContaining("could not start")
        .hasMessageMatching("(?s).*(ErrImagePull|ImagePullBackOff).*");
  }

  @Test
  void namesAreValidKubernetesNames() {
    assertThat(KubernetesWorkloadRuntime.kubernetesName("cf-data-" + UUID.randomUUID()))
        .matches("[a-z0-9-]{1,63}");
    assertThat(KubernetesWorkloadRuntime.kubernetesName("CF_My.App--x-")).isEqualTo("cf-my-app-x");
  }

  private static ContainerSpec spec(String name, String image) {
    return new ContainerSpec(
        DeploymentTarget.KUBERNETES,
        name,
        image,
        Map.of("WHOAMI_PORT_NUMBER", "8080", "CLOUDFLOW_ENVIRONMENT", "development"),
        8080,
        "cloudflow-apps",
        "cf-data-demo",
        "/data",
        new BigDecimal("0.50"),
        128,
        Map.of("cloudflow.managed", "true", "cloudflow.environment", "demo"));
  }

  private static <T> Optional<T> nonEmpty(T collection) {
    return ((java.util.Collection<?>) collection).isEmpty()
        ? Optional.empty()
        : Optional.of(collection);
  }

  private static <T> T await(java.util.function.Supplier<Optional<T>> probe)
      throws InterruptedException {
    Instant deadline = Instant.now().plusSeconds(90);
    while (Instant.now().isBefore(deadline)) {
      Optional<T> value = probe.get();
      if (value.isPresent()) {
        return value.get();
      }
      Thread.sleep(1000);
    }
    throw new AssertionError("Condition not met within 90s");
  }
}
