package com.cloudflow.storage;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentLog;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.domain.LogLevel;
import com.cloudflow.deployment.domain.LogPhase;
import com.cloudflow.deployment.domain.TriggerType;
import com.cloudflow.deployment.repository.DeploymentLogRepository;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.deployment.service.DeploymentStatusChangedEvent;
import com.cloudflow.storage.domain.ArtifactKind;
import com.cloudflow.storage.dto.ArtifactResponse;
import com.cloudflow.storage.service.ArtifactService;
import com.cloudflow.storage.service.ArtifactUpload;
import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestProjects;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.services.s3.S3Client;

/** Artifact storage against a real S3-compatible server (SeaweedFS). */
@IntegrationTest
class ArtifactStorageIntegrationTest {

  private static final String ACCESS_KEY = "cloudflow-test";
  private static final String SECRET_KEY = "cloudflow-test-secret";
  private static final String BUCKET = "cloudflow-artifacts";

  @SuppressWarnings("resource")
  static final GenericContainer<?> S3 =
      new GenericContainer<>("chrislusf/seaweedfs:4.47")
          .withEnv("S3_ACCESS_KEY", ACCESS_KEY)
          .withEnv("S3_SECRET_KEY", SECRET_KEY)
          .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh"))
          .withCommand(
              "-c",
              "printf '{\"identities\":[{\"name\":\"cloudflow\",\"credentials\":[{\"accessKey\":\"%s\","
                  + "\"secretKey\":\"%s\"}],\"actions\":[\"Admin\",\"Read\",\"Write\",\"List\"]}]}'"
                  + " \"$S3_ACCESS_KEY\" \"$S3_SECRET_KEY\" > /tmp/s3.json"
                  + " && exec weed mini -dir=/data -s3.config=/tmp/s3.json -bucket="
                  + BUCKET)
          .withExposedPorts(8333)
          .waitingFor(Wait.forListeningPorts(8333))
          .withStartupTimeout(Duration.ofMinutes(2));

  static {
    S3.start();
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add("cloudflow.storage.enabled", () -> "true");
    registry.add("cloudflow.storage.bucket", () -> BUCKET);
    registry.add(
        "cloudflow.storage.endpoint",
        () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(8333));
    registry.add("cloudflow.storage.path-style-access", () -> "true");
    registry.add("cloudflow.storage.access-key", () -> ACCESS_KEY);
    registry.add("cloudflow.storage.secret-key", () -> SECRET_KEY);
  }

  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private TestProjects projects;
  @Autowired private ArtifactService artifacts;
  @Autowired private DeploymentRepository deployments;
  @Autowired private DeploymentLogRepository logs;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private S3Client s3;

  private TestUser owner;
  private String organizationId;
  private UUID projectId;

  @BeforeEach
  void setUp() {
    owner = users.create("owner");
    organizationId = api.createOrganization(owner);
    projectId = UUID.fromString(projects.create(owner, organizationId, List.of("app.py")));
  }

  @Test
  void storesListsAndDownloadsArtifactsForProjectMembersOnly() throws Exception {
    ArtifactResponse stored =
        artifacts
            .store(
                ArtifactUpload.text(
                    projectId,
                    null,
                    null,
                    ArtifactKind.GENERATED_FILE,
                    ".github/workflows/cloudflow-development.yml",
                    "text/yaml; charset=utf-8",
                    "name: CloudFlow\n",
                    owner.id()))
            .orElseThrow();
    assertThat(stored.sizeBytes()).isEqualTo(16);
    assertThat(stored.sha256()).hasSize(64);

    MvcTestResult list = api.get(owner, "/api/v1/projects/" + projectId + "/artifacts");
    assertThat(list).hasStatusOk();
    assertThat(read(list, "$.content[0].name"))
        .isEqualTo(".github/workflows/cloudflow-development.yml");
    assertThat(read(list, "$.content[0].kind")).isEqualTo("GENERATED_FILE");

    TestUser viewer = api.addMember(owner, organizationId, "VIEWER");
    MvcTestResult download = api.get(viewer, "/api/v1/artifacts/" + stored.id() + "/content");
    assertThat(download).hasStatusOk();
    assertThat(download.getResponse().getContentAsString()).isEqualTo("name: CloudFlow\n");
    assertThat(download.getResponse().getHeader("Content-Disposition"))
        .startsWith("attachment")
        .contains("cloudflow-development.yml");

    TestUser outsider = users.create("outsider");
    assertThat(api.get(outsider, "/api/v1/artifacts/" + stored.id() + "/content")).hasStatus(404);
    assertThat(api.get(outsider, "/api/v1/projects/" + projectId + "/artifacts")).hasStatus(404);
  }

  @Test
  void finishedDeploymentLogsAreArchived() throws Exception {
    String environmentId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + projectId + "/environments",
                "{\"type\":\"DEVELOPMENT\"}"),
            "$.id");
    Deployment deployment =
        deployments.save(
            Deployment.build(
                projectId,
                UUID.fromString(environmentId),
                owner.id(),
                TriggerType.MANUAL,
                "main",
                null));
    logs.save(
        new DeploymentLog(
            deployment.getId(),
            LogPhase.BUILD,
            LogLevel.INFO,
            "Step 1/4 : FROM python",
            Instant.now()));

    events.publishEvent(
        new DeploymentStatusChangedEvent(
            deployment.getId(),
            deployment.getEnvironmentId(),
            projectId,
            DeploymentStatus.FAILED,
            TriggerType.MANUAL,
            null,
            "Health check did not pass"));

    ArtifactResponse archived = awaitArtifact(deployment.getId());
    assertThat(archived.kind()).isEqualTo(ArtifactKind.ARCHIVED_LOG);
    MvcTestResult content = api.get(owner, "/api/v1/artifacts/" + archived.id() + "/content");
    assertThat(content.getResponse().getContentAsString())
        .contains("BUILD INFO Step 1/4 : FROM python");
  }

  @Test
  void deletingAProjectRemovesItsObjects() throws Exception {
    artifacts
        .store(
            ArtifactUpload.text(
                projectId,
                null,
                null,
                ArtifactKind.BUILD_ARTIFACT,
                "Dockerfile",
                "text/plain",
                "FROM python:3.12-slim\n",
                owner.id()))
        .orElseThrow();
    String prefix = "projects/" + projectId + "/";
    assertThat(objectCount(prefix)).isEqualTo(1);

    assertThat(api.delete(owner, "/api/v1/projects/" + projectId)).hasStatus(204);

    assertThat(objectCount(prefix)).isZero();
  }

  private int objectCount(String prefix) {
    return s3.listObjectsV2(request -> request.bucket(BUCKET).prefix(prefix)).keyCount();
  }

  private ArtifactResponse awaitArtifact(UUID deploymentId) throws InterruptedException {
    Instant deadline = Instant.now().plusSeconds(20);
    while (Instant.now().isBefore(deadline)) {
      var page =
          artifacts.list(
              projectId,
              owner.id(),
              null,
              deploymentId,
              org.springframework.data.domain.PageRequest.of(0, 10));
      if (!page.content().isEmpty()) {
        return page.content().getFirst();
      }
      Thread.sleep(200);
    }
    throw new AssertionError("The deployment log was not archived");
  }
}
