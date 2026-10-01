package com.cloudflow.assistant.web;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudflow.assistant.client.AiModels.AnalysisRequest;
import com.cloudflow.assistant.client.AiModels.Answer;
import com.cloudflow.assistant.client.AiModels.Evidence;
import com.cloudflow.assistant.client.AiModels.GeneratedArtifact;
import com.cloudflow.assistant.client.AiModels.IndexRequest;
import com.cloudflow.assistant.client.AiModels.IndexResult;
import com.cloudflow.assistant.client.AiModels.QueryRequest;
import com.cloudflow.assistant.client.AiServiceClient;
import com.cloudflow.common.exception.ExternalServiceException;
import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentLog;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.domain.LogLevel;
import com.cloudflow.deployment.domain.LogPhase;
import com.cloudflow.deployment.domain.TriggerType;
import com.cloudflow.deployment.repository.DeploymentLogRepository;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.github.client.GithubClient;
import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestProjects;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AssistantIntegrationTest {

  private static final String SECRET = "p4ssw0rd-do-not-leak";
  private static final Answer ANSWER =
      new Answer(
          "The container exits because DATABASE_URL is missing.",
          "Missing DATABASE_URL",
          List.of(new Evidence("deployment-logs", "deployment-logs", "KeyError: 'DATABASE_URL'")),
          List.of("Add DATABASE_URL in the environment's Variables tab"),
          "HIGH",
          "The traceback names the missing variable.");

  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private TestProjects projects;
  @Autowired private AiServiceClient ai;
  @Autowired private GithubClient github;
  @Autowired private DeploymentRepository deployments;
  @Autowired private DeploymentLogRepository logs;

  private TestUser owner;
  private String orgId;
  private String projectId;
  private String envId;

  @BeforeEach
  void setUp() {
    owner = users.create("owner");
    orgId = api.createOrganization(owner);
    projectId = projects.create(owner, orgId, List.of("app.py", "requirements.txt"));
    envId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + projectId + "/environments",
                "{\"type\":\"DEVELOPMENT\"}"),
            "$.id");
    api.put(
        owner,
        "/api/v1/environments/" + envId + "/variables/DB_PASSWORD",
        "{\"value\":\"" + SECRET + "\",\"secret\":true}");
    api.put(
        owner,
        "/api/v1/environments/" + envId + "/variables/LOG_LEVEL",
        "{\"value\":\"debug\",\"secret\":false}");
    when(ai.enabled()).thenReturn(true);
  }

  @Test
  void questionsCarryLiveContextButNoSecrets() {
    when(ai.query(any())).thenReturn(ANSWER);

    MvcTestResult response =
        api.post(
            owner,
            "/api/v1/projects/" + projectId + "/assistant/query",
            "{\"question\":\"Why did my latest deployment fail?\"}");

    assertThat(response).hasStatusOk();
    assertThat(response)
        .bodyJson()
        .extractingPath("$.likelyCause")
        .isEqualTo("Missing DATABASE_URL");
    assertThat(response).bodyJson().extractingPath("$.confidence").isEqualTo("HIGH");
    ArgumentCaptor<QueryRequest> request = ArgumentCaptor.forClass(QueryRequest.class);
    verify(ai).query(request.capture());
    assertThat(request.getValue().question()).isEqualTo("Why did my latest deployment fail?");
    assertThat(request.getValue().liveContext()).containsKeys("project", "environments");
    assertThat(request.getValue().liveContext().toString()).doesNotContain(SECRET);
  }

  @Test
  void deploymentAnalysisSendsLogsConfigurationAndDockerfileWithoutSecrets() {
    Deployment failed = failedDeployment();
    when(ai.analyzeDeployment(any())).thenReturn(ANSWER);

    assertThat(api.post(owner, "/api/v1/deployments/" + failed.getId() + "/analysis", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.recommendedActions[0]")
        .isEqualTo("Add DATABASE_URL in the environment's Variables tab");

    ArgumentCaptor<AnalysisRequest> request = ArgumentCaptor.forClass(AnalysisRequest.class);
    verify(ai).analyzeDeployment(request.capture());
    AnalysisRequest sent = request.getValue();
    assertThat(sent.logs()).anyMatch(line -> line.contains("KeyError: 'DATABASE_URL'"));
    assertThat(sent.deployment())
        .containsEntry("failureReason", "The container stopped during startup");
    assertThat(sent.dockerfile()).contains("FROM python:3.12-slim");
    @SuppressWarnings("unchecked")
    Map<String, String> variables = (Map<String, String>) sent.configuration().get("variables");
    assertThat(variables)
        .containsEntry("DB_PASSWORD", "<secret, value hidden>")
        .containsEntry("LOG_LEVEL", "debug");
    assertThat(sent.toString()).doesNotContain(SECRET);
  }

  @Test
  void reindexSendsProjectKnowledgeWithoutSecretValues() {
    failedDeployment();
    when(github.getFileContent(anyString(), eq("octo"), anyString(), eq("app.py"), anyString()))
        .thenReturn("import os\nprint(os.environ['DATABASE_URL'])");
    when(ai.index(any())).thenReturn(new IndexResult(3, 0, 0, 4));

    assertThat(api.post(owner, "/api/v1/projects/" + projectId + "/knowledge/reindex", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.indexed")
        .isEqualTo(3);

    ArgumentCaptor<IndexRequest> request = ArgumentCaptor.forClass(IndexRequest.class);
    verify(ai).index(request.capture());
    assertThat(request.getValue().replaceAll()).isTrue();
    assertThat(request.getValue().documents())
        .extracting(document -> document.sourceRef())
        .contains("file:app.py", "environment:development");
    assertThat(request.getValue().documents())
        .anyMatch(document -> document.sourceType().equals("LOG"));
    assertThat(request.getValue().toString()).doesNotContain(SECRET);
  }

  @Test
  void generatedFilesAreOnlyCommittedAfterApprovalToASafePath() {
    when(ai.generate(eq("DOCKERFILE"), any()))
        .thenReturn(
            new GeneratedArtifact("../../etc/passwd", "FROM python:3.12-slim", "Python app"));
    when(github.putFile(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any()))
        .thenReturn("abcdef1234567890abcdef1234567890abcdef12");

    MvcTestResult generated =
        api.post(
            owner, "/api/v1/projects/" + projectId + "/suggestions", "{\"type\":\"DOCKERFILE\"}");
    assertThat(generated).hasStatus(HttpStatus.CREATED);
    assertThat(generated).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
    assertThat(generated).bodyJson().extractingPath("$.filePath").isEqualTo("Dockerfile");
    String suggestionId = read(generated, "$.id");

    TestUser viewer = api.addMember(owner, orgId, "VIEWER");
    assertThat(api.post(viewer, "/api/v1/suggestions/" + suggestionId + "/apply", "{}"))
        .hasStatus(HttpStatus.FORBIDDEN);

    assertThat(api.post(owner, "/api/v1/suggestions/" + suggestionId + "/apply", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.result")
        .asString()
        .contains("Committed Dockerfile to main (abcdef1)");
    verify(github)
        .putFile(
            anyString(),
            eq("octo"),
            anyString(),
            eq("Dockerfile"),
            eq("main"),
            anyString(),
            eq("FROM python:3.12-slim\n"),
            isNull());
    assertThat(api.post(owner, "/api/v1/suggestions/" + suggestionId + "/apply", "{}"))
        .hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void environmentTemplatesCreateMissingVariablesButNeverPlaceholderSecrets() {
    when(ai.generate(eq("ENV_TEMPLATE"), any()))
        .thenReturn(
            new GeneratedArtifact(
                "variables", "PORT=8000\nLOG_LEVEL=info\nAPI_KEY=changeme # secret\n", "Template"));
    String suggestionId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + projectId + "/suggestions",
                "{\"type\":\"ENV_TEMPLATE\",\"environmentId\":\"" + envId + "\"}"),
            "$.id");

    assertThat(api.post(owner, "/api/v1/suggestions/" + suggestionId + "/apply", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.result")
        .asString()
        .contains("Created 1 variable(s): PORT")
        .contains("Kept existing: LOG_LEVEL")
        .contains("Set these secrets yourself: API_KEY");
    assertThat(api.get(owner, "/api/v1/environments/" + envId + "/variables"))
        .bodyJson()
        .extractingPath("$[*].key")
        .asArray()
        .containsExactlyInAnyOrder("DB_PASSWORD", "LOG_LEVEL", "PORT");
  }

  @Test
  void rejectedSuggestionsCannotBeApplied() {
    when(ai.generate(eq("DOCUMENTATION"), any()))
        .thenReturn(new GeneratedArtifact("docs/SETUP.md", "# Setup", "Docs"));
    String suggestionId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + projectId + "/suggestions",
                "{\"type\":\"DOCUMENTATION\"}"),
            "$.id");

    assertThat(api.post(owner, "/api/v1/suggestions/" + suggestionId + "/reject", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("REJECTED");
    assertThat(api.post(owner, "/api/v1/suggestions/" + suggestionId + "/apply", "{}"))
        .hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void aiOutagesOnlyAffectAiFeatures() {
    when(ai.query(any()))
        .thenThrow(
            new ExternalServiceException("AI service", "The AI service could not be reached"));

    assertThat(
            api.post(
                owner,
                "/api/v1/projects/" + projectId + "/assistant/query",
                "{\"question\":\"What is deployed?\"}"))
        .hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson()
        .extractingPath("$.service")
        .isEqualTo("AI service");
    assertThat(api.get(owner, "/api/v1/projects/" + projectId)).hasStatusOk();
  }

  @Test
  void knowledgeStatusReportsDisabledAi() {
    when(ai.enabled()).thenReturn(false);

    assertThat(api.get(owner, "/api/v1/projects/" + projectId + "/knowledge"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.enabled")
        .isEqualTo(false);
  }

  private Deployment failedDeployment() {
    Deployment deployment =
        Deployment.build(
            UUID.fromString(projectId),
            UUID.fromString(envId),
            owner.id(),
            TriggerType.MANUAL,
            "main",
            TestProjects.HEAD_SHA);
    deployment.start(Instant.now());
    deployment.advanceTo(DeploymentStatus.BUILDING);
    deployment.fail("The container stopped during startup", Instant.now());
    Deployment saved = deployments.save(deployment);
    logs.save(
        new DeploymentLog(
            saved.getId(), LogPhase.BUILD, LogLevel.INFO, "Step 1/6 : FROM python", Instant.now()));
    logs.save(
        new DeploymentLog(
            saved.getId(),
            LogPhase.RUNTIME,
            LogLevel.INFO,
            "KeyError: 'DATABASE_URL'",
            Instant.now()));
    logs.save(
        new DeploymentLog(
            saved.getId(),
            LogPhase.DEPLOY,
            LogLevel.ERROR,
            "The container stopped during startup",
            Instant.now()));
    return saved;
  }
}
