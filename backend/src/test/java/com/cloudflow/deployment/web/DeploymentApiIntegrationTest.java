package com.cloudflow.deployment.web;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.domain.ScanStatus;
import com.cloudflow.deployment.domain.TriggerType;
import com.cloudflow.deployment.engine.ContainerRuntime;
import com.cloudflow.deployment.engine.ContainerRuntime.RuntimeFailure;
import com.cloudflow.deployment.engine.ContainerSpec;
import com.cloudflow.deployment.engine.ContainerState;
import com.cloudflow.deployment.engine.HealthChecker;
import com.cloudflow.deployment.engine.HealthProbe;
import com.cloudflow.deployment.engine.ImageScanner;
import com.cloudflow.deployment.engine.RunningContainer;
import com.cloudflow.deployment.engine.ScanResult;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestProjects;
import com.cloudflow.support.TestProjects.TestProject;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class DeploymentApiIntegrationTest {

  private static final String SECRET = "super-secret-password";

  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private TestProjects projects;
  @Autowired private ContainerRuntime runtime;
  @Autowired private HealthChecker healthChecker;
  @Autowired private ImageScanner imageScanner;
  @Autowired private DeploymentRepository deploymentRepository;

  private final AtomicInteger containers = new AtomicInteger();
  private TestUser owner;
  private String orgId;
  private TestProject project;
  private String devEnvId;

  @BeforeEach
  void setUp() {
    owner = users.create("owner");
    orgId = api.createOrganization(owner);
    project =
        projects.createWithSource(
            owner, orgId, Map.of("app.py", "print('hi')", "requirements.txt", "flask", "src/", ""));
    devEnvId = createEnvironment(owner, "DEVELOPMENT");
    api.put(
        owner,
        "/api/v1/environments/" + devEnvId + "/variables/DB_PASSWORD",
        "{\"value\":\"" + SECRET + "\",\"secret\":true}");
    api.put(
        owner,
        "/api/v1/environments/" + devEnvId + "/variables/GREETING",
        "{\"value\":\"hello\",\"secret\":false}");

    when(runtime.build(any(Path.class), any(Path.class), anyString(), any()))
        .thenAnswer(
            invocation -> {
              Consumer<String> output = invocation.getArgument(3);
              output.accept("Step 1/5 : FROM python:3.12-slim");
              output.accept("echo leaking " + SECRET);
              return "sha256:" + "a".repeat(64);
            });
    when(runtime.run(any(ContainerSpec.class)))
        .thenAnswer(
            invocation -> {
              ContainerSpec spec = invocation.getArgument(0);
              return new RunningContainer(
                  "container-" + containers.incrementAndGet(), spec.name(), 32768);
            });
    when(runtime.inspect(anyString()))
        .thenReturn(new ContainerState(true, true, "running", null, 0, Instant.now()));
    when(runtime.imageExists(anyString())).thenReturn(true);
    when(runtime.tailLogs(anyString(), anyInt()))
        .thenReturn(List.of("Traceback: ModuleNotFoundError: flask"));
    when(healthChecker.probe(any())).thenReturn(new HealthProbe(true, 200, 5, null));
  }

  @Test
  void deploysBuildsRunsAndActivates() throws Exception {
    MvcTestResult triggered = trigger(owner, devEnvId);
    assertThat(triggered).hasStatus(HttpStatus.ACCEPTED);
    assertThat(triggered).bodyJson().extractingPath("$.status").isEqualTo("QUEUED");
    String deploymentId = read(triggered, "$.id");

    Deployment deployment = awaitFinished(deploymentId);

    assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCEEDED);
    assertThat(deployment.isActive()).isTrue();
    assertThat(deployment.getCommitSha()).isEqualTo(TestProjects.HEAD_SHA);
    assertThat(deployment.getCommitMessage()).isEqualTo("Deployable change");
    assertThat(deployment.getImageTag())
        .startsWith("localhost:5000/")
        .contains(":development-0123456-");
    assertThat(deployment.getHostPort()).isEqualTo(32768);

    ArgumentCaptor<ContainerSpec> spec = ArgumentCaptor.forClass(ContainerSpec.class);
    verify(runtime).run(spec.capture());
    assertThat(spec.getValue().environment())
        .containsEntry("DB_PASSWORD", SECRET)
        .containsEntry("GREETING", "hello")
        .containsEntry("PORT", "8000")
        .containsEntry("CLOUDFLOW_ENVIRONMENT", "development")
        .containsEntry("CLOUDFLOW_DEPLOYMENT_ID", deploymentId);
    assertThat(spec.getValue().network()).isEqualTo("cloudflow-apps");
    assertThat(spec.getValue().volumeName()).isEqualTo("cf-data-" + devEnvId);
    verify(runtime).push(eq(deployment.getImageTag()), any());

    MvcTestResult details = api.get(owner, "/api/v1/deployments/" + deploymentId);
    assertThat(details).bodyJson().extractingPath("$.url").isEqualTo("http://localhost:32768");

    String logs =
        api.get(owner, "/api/v1/deployments/" + deploymentId + "/logs")
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(logs)
        .contains("Generated a Dockerfile from the Python template")
        .contains("Deployment is live")
        .contains("echo leaking ********")
        .doesNotContain(SECRET);
  }

  @Test
  void recordsImageScanResultsAndLogsFindings() throws Exception {
    when(imageScanner.scan(anyString(), any()))
        .thenReturn(
            Optional.of(
                new ScanResult(
                    1,
                    2,
                    List.of(
                        new ScanResult.Finding(
                            "CVE-2026-0001", "CRITICAL", "openssl", "3.0.1", "3.0.9", "RCE")))));

    Deployment deployment = awaitFinished(read(trigger(owner, devEnvId), "$.id"));

    // The default policy reports vulnerabilities without blocking the deployment.
    assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCEEDED);
    assertThat(deployment.getScanStatus()).isEqualTo(ScanStatus.VULNERABLE);
    assertThat(deployment.getVulnerabilitiesCritical()).isEqualTo(1);
    assertThat(deployment.getVulnerabilitiesHigh()).isEqualTo(2);
    assertThat(
            api.get(owner, "/api/v1/deployments/" + deployment.getId() + "/logs")
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8))
        .contains("1 critical, 2 high vulnerabilities")
        .contains("CRITICAL CVE-2026-0001 in openssl 3.0.1 → fixed in 3.0.9: RCE");
  }

  @Test
  void aNewDeploymentReplacesThePreviousContainer() {
    Deployment first = awaitFinished(read(trigger(owner, devEnvId), "$.id"));
    Deployment second = awaitFinished(read(trigger(owner, devEnvId), "$.id"));

    assertThat(second.getStatus()).isEqualTo(DeploymentStatus.SUCCEEDED);
    assertThat(second.isActive()).isTrue();
    assertThat(deploymentRepository.findById(first.getId()).orElseThrow().isActive()).isFalse();
    verify(runtime).remove(first.getContainerId());
  }

  @Test
  void failedHealthCheckKeepsThePreviousDeploymentServing() {
    Deployment healthy = awaitFinished(read(trigger(owner, devEnvId), "$.id"));
    when(healthChecker.probe(any())).thenReturn(new HealthProbe(false, 503, 5, null));

    String failedId = read(trigger(owner, devEnvId), "$.id");
    Deployment failed = awaitFinished(failedId);

    assertThat(failed.getStatus()).isEqualTo(DeploymentStatus.FAILED);
    assertThat(failed.getFailureReason()).contains("Health check did not pass");
    verify(runtime).remove(failed.getContainerId());
    verify(runtime, never()).remove(healthy.getContainerId());
    assertThat(deploymentRepository.findById(healthy.getId()).orElseThrow().isActive()).isTrue();
    assertThat(api.get(owner, "/api/v1/deployments/" + failedId + "/logs?afterId=0"))
        .bodyJson()
        .extractingPath("$[?(@.phase == 'RUNTIME')].message")
        .asArray()
        .contains("Traceback: ModuleNotFoundError: flask");
  }

  @Test
  void crashingContainerFailsFast() {
    when(runtime.inspect(anyString()))
        .thenReturn(new ContainerState(true, false, "exited", 1, 0, null));

    Deployment failed = awaitFinished(read(trigger(owner, devEnvId), "$.id"));

    assertThat(failed.getStatus()).isEqualTo(DeploymentStatus.FAILED);
    assertThat(failed.getFailureReason()).contains("exit code 1");
  }

  @Test
  void buildFailureIsRecordedWithoutStartingAContainer() {
    when(runtime.build(any(Path.class), any(Path.class), anyString(), any()))
        .thenThrow(new RuntimeFailure("Image build failed: exit code 1"));

    Deployment failed = awaitFinished(read(trigger(owner, devEnvId), "$.id"));

    assertThat(failed.getStatus()).isEqualTo(DeploymentStatus.FAILED);
    assertThat(failed.getFailureReason()).isEqualTo("Image build failed: exit code 1");
    verify(runtime, never()).run(any());
  }

  @Test
  void rollbackRedeploysAnEarlierImage() {
    Deployment first = awaitFinished(read(trigger(owner, devEnvId), "$.id"));
    Deployment second = awaitFinished(read(trigger(owner, devEnvId), "$.id"));

    MvcTestResult rollback =
        api.post(owner, "/api/v1/deployments/" + first.getId() + "/rollback", "{}");
    assertThat(rollback).hasStatus(HttpStatus.ACCEPTED);
    Deployment rolledBack = awaitFinished(read(rollback, "$.id"));

    assertThat(rolledBack.getTriggerType()).isEqualTo(TriggerType.ROLLBACK);
    assertThat(rolledBack.getStatus()).isEqualTo(DeploymentStatus.SUCCEEDED);
    assertThat(rolledBack.getImageTag()).isEqualTo(first.getImageTag());
    assertThat(rolledBack.getRollbackOfId()).isEqualTo(first.getId());
    assertThat(deploymentRepository.findById(second.getId()).orElseThrow().getStatus())
        .isEqualTo(DeploymentStatus.ROLLED_BACK);
    // The image was reused, not rebuilt.
    verify(runtime, times(2)).build(any(Path.class), any(Path.class), anyString(), any());

    assertThat(api.post(owner, "/api/v1/deployments/" + rolledBack.getId() + "/rollback", "{}"))
        .hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void invalidConfigurationBlocksDeployment() {
    api.put(
        owner,
        "/api/v1/environments/" + devEnvId + "/config",
        """
        {"template":"PYTHON","runtimeVersion":"3.12","startCommand":"",
         "dockerfilePath":"Dockerfile","containerPort":8000,"healthCheckPath":"/"}
        """);

    assertThat(trigger(owner, devEnvId))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .contains("startCommand");
  }

  @Test
  void onlyOneDeploymentPerEnvironmentCanBeInProgress() {
    Deployment queued =
        deploymentRepository.save(
            Deployment.build(
                UUID.fromString(project.id()),
                UUID.fromString(devEnvId),
                owner.id(),
                TriggerType.MANUAL,
                "main",
                null));

    assertThat(trigger(owner, devEnvId)).hasStatus(HttpStatus.CONFLICT);

    assertThat(api.post(owner, "/api/v1/deployments/" + queued.getId() + "/cancel", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("CANCELLED");
    assertThat(trigger(owner, devEnvId)).hasStatus(HttpStatus.ACCEPTED);
  }

  @Test
  void enforcesDeploymentPermissions() {
    TestUser developer = api.addMember(owner, orgId, "DEVELOPER");
    TestUser viewer = api.addMember(owner, orgId, "VIEWER");
    String productionId = createEnvironment(owner, "PRODUCTION");

    assertThat(trigger(viewer, devEnvId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(trigger(developer, productionId)).hasStatus(HttpStatus.FORBIDDEN);

    String deploymentId = read(trigger(developer, devEnvId), "$.id");
    awaitFinished(deploymentId);
    assertThat(api.get(viewer, "/api/v1/deployments/" + deploymentId)).hasStatusOk();
    assertThat(api.get(viewer, "/api/v1/environments/" + devEnvId + "/deployments"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.totalElements")
        .isEqualTo(1);
    assertThat(api.get(viewer, "/api/v1/projects/" + project.id() + "/deployments"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.content[0].id")
        .isEqualTo(deploymentId);

    TestUser outsider = users.create("outsider");
    assertThat(api.get(outsider, "/api/v1/deployments/" + deploymentId))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  private MvcTestResult trigger(TestUser user, String environmentId) {
    return api.post(user, "/api/v1/environments/" + environmentId + "/deployments", "{}");
  }

  private String createEnvironment(TestUser user, String type) {
    MvcTestResult created =
        api.post(
            user,
            "/api/v1/projects/" + project.id() + "/environments",
            "{\"type\":\"" + type + "\"}");
    assertThat(created).hasStatus(HttpStatus.CREATED);
    return read(created, "$.id");
  }

  private Deployment awaitFinished(String deploymentId) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (Instant.now().isBefore(deadline)) {
      Deployment deployment =
          deploymentRepository.findById(UUID.fromString(deploymentId)).orElseThrow();
      if (!deployment.getStatus().isInProgress()) {
        return deployment;
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new AssertionError("Deployment " + deploymentId + " did not finish in time");
  }
}
