package com.cloudflow.environment.web;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.environment.domain.EnvironmentVariable;
import com.cloudflow.environment.repository.EnvironmentVariableRepository;
import com.cloudflow.environment.service.VariableService;
import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestProjects;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class EnvironmentApiIntegrationTest {

  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private TestProjects projects;
  @Autowired private EnvironmentVariableRepository variableRepository;
  @Autowired private VariableService variableService;

  private TestUser owner;
  private String orgId;
  private String projectId;

  @BeforeEach
  void setUp() {
    owner = users.create("owner");
    orgId = api.createOrganization(owner);
    projectId = projects.create(owner, orgId, List.of("pom.xml"));
  }

  @Test
  void createsEnvironmentWithRecommendedTemplateDefaults() {
    MvcTestResult created = createEnvironment(owner, "DEVELOPMENT", "develop");

    assertThat(created).hasStatus(HttpStatus.CREATED);
    assertThat(created).bodyJson().extractingPath("$.branch").isEqualTo("develop");
    assertThat(created).bodyJson().extractingPath("$.config.template").isEqualTo("JAVA");
    assertThat(created)
        .bodyJson()
        .extractingPath("$.config.buildCommand")
        .isEqualTo("mvn -B -DskipTests package");
    assertThat(created).bodyJson().extractingPath("$.config.containerPort").isEqualTo(8080);

    assertThat(createEnvironment(owner, "DEVELOPMENT", null)).hasStatus(HttpStatus.CONFLICT);
    assertThat(createEnvironment(owner, "STAGING", "does-not-exist"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(createEnvironment(owner, "STAGING", null))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .extractingPath("$.branch")
        .isEqualTo("main");
    assertThat(api.get(owner, "/api/v1/projects/" + projectId + "/environments"))
        .bodyJson()
        .extractingPath("$[*].type")
        .asArray()
        .containsExactly("DEVELOPMENT", "STAGING");
  }

  @Test
  void productionRequiresProductionPermission() {
    TestUser developer = api.addMember(owner, orgId, "DEVELOPER");
    TestUser admin = api.addMember(owner, orgId, "ADMIN");

    assertThat(createEnvironment(developer, "PRODUCTION", null)).hasStatus(HttpStatus.FORBIDDEN);
    String prodId = read(createEnvironment(admin, "PRODUCTION", null), "$.id");
    String devId = read(createEnvironment(developer, "DEVELOPMENT", null), "$.id");

    assertThat(putVariable(developer, devId, "LOG_LEVEL", "debug", false))
        .hasStatus(HttpStatus.CREATED);
    assertThat(putVariable(developer, prodId, "LOG_LEVEL", "info", false))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(api.put(developer, "/api/v1/environments/" + prodId + "/config", javaConfig()))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(api.get(developer, "/api/v1/environments/" + prodId + "/variables")).hasStatusOk();
    assertThat(putVariable(admin, prodId, "LOG_LEVEL", "info", false))
        .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void viewersAndOutsidersCannotChangeEnvironments() {
    String envId = read(createEnvironment(owner, "DEVELOPMENT", null), "$.id");
    TestUser viewer = api.addMember(owner, orgId, "VIEWER");
    TestUser outsider = users.create("outsider");

    assertThat(putVariable(viewer, envId, "A", "b", false)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(api.get(viewer, "/api/v1/environments/" + envId)).hasStatusOk();
    assertThat(api.get(outsider, "/api/v1/environments/" + envId)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(api.get(outsider, "/api/v1/projects/" + projectId + "/environments"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void secretsAreEncryptedAtRestAndNeverReturned() {
    String envId = read(createEnvironment(owner, "DEVELOPMENT", null), "$.id");

    assertThat(putVariable(owner, envId, "DATABASE_PASSWORD", "hunter2", true))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .extractingPath("$.value")
        .isNull();
    assertThat(putVariable(owner, envId, "APP_MODE", "demo", false)).hasStatus(HttpStatus.CREATED);

    MvcTestResult list = api.get(owner, "/api/v1/environments/" + envId + "/variables");
    assertThat(list).hasStatusOk();
    assertThat(list).bodyJson().extractingPath("$[0].key").isEqualTo("APP_MODE");
    assertThat(list).bodyJson().extractingPath("$[0].value").isEqualTo("demo");
    assertThat(list).bodyJson().extractingPath("$[1].key").isEqualTo("DATABASE_PASSWORD");
    assertThat(list).bodyJson().extractingPath("$[1].value").isNull();
    assertThat(list).bodyJson().extractingPath("$[1].secret").isEqualTo(true);

    EnvironmentVariable stored =
        variableRepository
            .findByEnvironmentIdAndKey(UUID.fromString(envId), "DATABASE_PASSWORD")
            .orElseThrow();
    assertThat(stored.getStoredValue()).startsWith("v1:").doesNotContain("hunter2");
    assertThat(variableService.resolveForDeployment(UUID.fromString(envId)))
        .containsEntry("DATABASE_PASSWORD", "hunter2")
        .containsEntry("APP_MODE", "demo");
    assertThat(api.get(owner, "/api/v1/projects/" + projectId + "/environments"))
        .bodyJson()
        .extractingPath("$[0].secretCount")
        .isEqualTo(1);

    assertThat(putVariable(owner, envId, "DATABASE_PASSWORD", "rotated", true))
        .hasStatus(HttpStatus.OK);
    assertThat(api.delete(owner, "/api/v1/environments/" + envId + "/variables/APP_MODE"))
        .hasStatus(HttpStatus.NO_CONTENT);
    assertThat(variableService.resolveForDeployment(UUID.fromString(envId)))
        .containsExactly(Map.entry("DATABASE_PASSWORD", "rotated"));
  }

  @Test
  void rejectsInvalidAndReservedVariableKeys() {
    String envId = read(createEnvironment(owner, "DEVELOPMENT", null), "$.id");

    assertThat(putVariable(owner, envId, "lower_case", "x", false))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(putVariable(owner, envId, "1ABC", "x", false)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(putVariable(owner, envId, "CLOUDFLOW_TOKEN", "x", false))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("key");
  }

  @Test
  void configurationCanBeUpdatedAndValidated() {
    String envId = read(createEnvironment(owner, "DEVELOPMENT", null), "$.id");

    assertThat(api.post(owner, "/api/v1/environments/" + envId + "/config/validate", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.valid")
        .isEqualTo(true);

    String nodeWithoutStart =
        """
        {"template":"NODE","runtimeVersion":"22","buildCommand":"npm ci","startCommand":"",
         "dockerfilePath":"Dockerfile","containerPort":3000,"healthCheckPath":"/health",
         "cpuLimit":0.5,"memoryLimitMb":256}
        """;
    assertThat(api.put(owner, "/api/v1/environments/" + envId + "/config", nodeWithoutStart))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.healthCheckPath")
        .isEqualTo("/health");

    MvcTestResult validation =
        api.post(owner, "/api/v1/environments/" + envId + "/config/validate", "{}");
    assertThat(validation).bodyJson().extractingPath("$.valid").isEqualTo(false);
    assertThat(validation)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .contains("startCommand");
    assertThat(validation)
        .bodyJson()
        .extractingPath("$.warnings[*].field")
        .asArray()
        .contains("template");

    assertThat(
            api.put(
                owner,
                "/api/v1/environments/" + envId + "/config",
                "{\"template\":\"JAVA\",\"dockerfilePath\":\"Dockerfile\",\"containerPort\":70000,"
                    + "\"healthCheckPath\":\"/\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .contains("containerPort");
  }

  @Test
  void listsTemplatesWithRecommendationForApplicationType() {
    assertThat(api.get(owner, "/api/v1/config-templates?appType=JAVA_GRADLE"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[?(@.recommended == true)].template")
        .asArray()
        .containsExactly("JAVA");
    assertThat(api.get(owner, "/api/v1/config-templates?appType=JAVA_GRADLE"))
        .bodyJson()
        .extractingPath("$[0].defaults.buildCommand")
        .isEqualTo("gradle build -x test --no-daemon");
  }

  @Test
  void deletingAProjectRemovesItsEnvironments() {
    String envId = read(createEnvironment(owner, "DEVELOPMENT", null), "$.id");
    assertThat(api.delete(owner, "/api/v1/projects/" + projectId)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(api.get(owner, "/api/v1/environments/" + envId)).hasStatus(HttpStatus.NOT_FOUND);
  }

  private MvcTestResult createEnvironment(TestUser user, String type, String branch) {
    String body =
        branch == null
            ? "{\"type\":\"" + type + "\"}"
            : "{\"type\":\"" + type + "\",\"branch\":\"" + branch + "\"}";
    return api.post(user, "/api/v1/projects/" + projectId + "/environments", body);
  }

  private MvcTestResult putVariable(
      TestUser user, String envId, String key, String value, boolean secret) {
    return api.put(
        user,
        "/api/v1/environments/" + envId + "/variables/" + key,
        "{\"value\":\"" + value + "\",\"secret\":" + secret + "}");
  }

  private static String javaConfig() {
    return """
        {"template":"JAVA","runtimeVersion":"21","buildCommand":"mvn -B package",
         "startCommand":"java -jar app.jar","dockerfilePath":"Dockerfile","containerPort":8080,
         "healthCheckPath":"/","cpuLimit":1,"memoryLimitMb":512}
        """;
  }
}
