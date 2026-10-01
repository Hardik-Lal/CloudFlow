package com.cloudflow.monitoring;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.engine.ContainerRuntime;
import com.cloudflow.deployment.engine.ContainerSpec;
import com.cloudflow.deployment.engine.ContainerState;
import com.cloudflow.deployment.engine.ContainerStats;
import com.cloudflow.deployment.engine.HealthChecker;
import com.cloudflow.deployment.engine.HealthProbe;
import com.cloudflow.deployment.engine.RunningContainer;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.monitoring.service.MonitoringCollector;
import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestProjects;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@IntegrationTest
class MonitoringIntegrationTest {

  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private TestProjects projects;
  @Autowired private ContainerRuntime runtime;
  @Autowired private HealthChecker healthChecker;
  @Autowired private MonitoringCollector collector;
  @Autowired private DeploymentRepository deployments;
  @Autowired private MeterRegistry meterRegistry;

  private TestUser owner;
  private String orgId;
  private String envId;

  @BeforeEach
  void deployAnApplication() {
    owner = users.create("owner");
    orgId = api.createOrganization(owner);
    String projectId =
        projects.createWithSource(owner, orgId, Map.of("app.py", "", "requirements.txt", "")).id();
    envId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + projectId + "/environments",
                "{\"type\":\"DEVELOPMENT\"}"),
            "$.id");

    when(runtime.build(any(Path.class), any(Path.class), anyString(), any()))
        .thenReturn("sha256:" + "b".repeat(64));
    when(runtime.run(any(ContainerSpec.class)))
        .thenAnswer(
            call -> {
              ContainerSpec spec = call.getArgument(0);
              return new RunningContainer("container-" + UUID.randomUUID(), spec.name(), 40001);
            });
    runningContainer(0);
    healthyProbe(true, 200, 42);

    String deploymentId =
        read(api.post(owner, "/api/v1/environments/" + envId + "/deployments", "{}"), "$.id");
    awaitSucceeded(deploymentId);
  }

  @Test
  void samplesLiveDeploymentsIntoMetricsAndPrometheus() {
    when(runtime.stats(anyString()))
        .thenReturn(Optional.of(new ContainerStats(12.5, 64L << 20, 512L << 20, 1000, 2000)));

    collector.collectAll();

    assertThat(api.get(owner, metricsPath()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
    var metrics = api.get(owner, metricsPath());
    assertThat(metrics).bodyJson().extractingPath("$.current.cpuPercent").isEqualTo(12.5);
    assertThat(metrics).bodyJson().extractingPath("$.current.memoryBytes").isEqualTo(64 << 20);
    assertThat(metrics).bodyJson().extractingPath("$.current.responseTimeMs").isEqualTo(42);
    assertThat(metrics).bodyJson().extractingPath("$.health.uptimePercent").isEqualTo(100.0);
    assertThat(metrics).bodyJson().extractingPath("$.history.length()").isEqualTo(1);

    Gauge cpu = meterRegistry.find("cloudflow.app.cpu.usage").tag("environment_id", envId).gauge();
    assertThat(cpu).isNotNull();
    assertThat(cpu.value()).isEqualTo(12.5);
    assertThat(cpu.getId().getTag("environment")).isEqualTo("development");
  }

  @Test
  void healthTransitionsAndRestartsBecomeEvents() {
    collector.collectAll();

    healthyProbe(false, 503, 15);
    runningContainer(1);
    collector.collectAll();

    var metrics = api.get(owner, metricsPath());
    assertThat(metrics).bodyJson().extractingPath("$.status").isEqualTo("DEGRADED");
    assertThat(metrics).bodyJson().extractingPath("$.health.errorRatePercent").isEqualTo(50.0);

    healthyProbe(true, 200, 20);
    collector.collectAll();

    assertThat(api.get(owner, "/api/v1/environments/" + envId + "/events"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].type")
        .asArray()
        .containsSubsequence(
            "HEALTH_RECOVERED", "HEALTH_DEGRADED", "DEPLOYMENT_SUCCEEDED", "DEPLOYMENT_STARTED")
        .contains("CONTAINER_RESTARTED");
  }

  @Test
  void stoppedContainersAreReportedDown() {
    collector.collectAll();
    when(runtime.inspect(anyString()))
        .thenReturn(new ContainerState(true, false, "exited", 137, 0, null));

    collector.collectAll();

    assertThat(api.get(owner, metricsPath()))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("DOWN");
    assertThat(api.get(owner, "/api/v1/environments/" + envId + "/events"))
        .bodyJson()
        .extractingPath("$[0].message")
        .asString()
        .contains("exit code 137");
  }

  @Test
  void environmentsWithoutDeploymentsAreNotDeployed() {
    String stagingId =
        read(
            api.post(
                owner,
                "/api/v1/projects/"
                    + deployments.findAll().stream()
                        .filter(d -> d.getEnvironmentId().toString().equals(envId))
                        .findFirst()
                        .map(Deployment::getProjectId)
                        .orElseThrow()
                    + "/environments",
                "{\"type\":\"STAGING\"}"),
            "$.id");

    assertThat(api.get(owner, "/api/v1/environments/" + stagingId + "/metrics"))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("NOT_DEPLOYED");
  }

  @Test
  void metricsFollowDeploymentReadPermissions() {
    TestUser viewer = api.addMember(owner, orgId, "VIEWER");
    TestUser outsider = users.create("outsider");

    assertThat(api.get(viewer, metricsPath())).hasStatusOk();
    assertThat(api.get(outsider, metricsPath())).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(api.get(outsider, "/api/v1/environments/" + envId + "/events"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void prometheusIsNotExposedOnTheApiPort() {
    assertThat(api.get(owner, "/actuator/prometheus")).hasStatus(HttpStatus.FORBIDDEN);
  }

  private String metricsPath() {
    return "/api/v1/environments/" + envId + "/metrics";
  }

  private void runningContainer(int restarts) {
    when(runtime.inspect(anyString()))
        .thenReturn(
            new ContainerState(
                true, true, "running", null, restarts, Instant.now().minusSeconds(120)));
  }

  private void healthyProbe(boolean healthy, int status, long millis) {
    when(healthChecker.probe(any())).thenReturn(new HealthProbe(healthy, status, millis, null));
  }

  private void awaitSucceeded(String deploymentId) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (Instant.now().isBefore(deadline)) {
      Deployment deployment = deployments.findById(UUID.fromString(deploymentId)).orElseThrow();
      if (deployment.isActive()) {
        return;
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    throw new AssertionError("Deployment did not become active");
  }
}
