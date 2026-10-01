package com.cloudflow.cicd.web;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.TriggerType;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.github.client.GithubClient;
import com.cloudflow.github.client.GithubModels.Account;
import com.cloudflow.github.client.GithubModels.HeadCommit;
import com.cloudflow.github.client.GithubModels.WorkflowJob;
import com.cloudflow.github.client.GithubModels.WorkflowRun;
import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestProjects;
import com.cloudflow.support.TestProjects.TestProject;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class CicdApiIntegrationTest {

  private static final String TOKEN_HEADER = "X-CloudFlow-Deploy-Token";
  private static final String WEBHOOK_SECRET = "test-webhook-secret";
  private static final String WORKFLOW_PATH = ".github/workflows/cloudflow-development.yml";

  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private TestProjects projects;
  @Autowired private GithubClient github;
  @Autowired private MockMvcTester mvc;
  @Autowired private DeploymentRepository deploymentRepository;

  private TestUser owner;
  private String orgId;
  private TestProject project;
  private String devEnvId;

  @BeforeEach
  void setUp() {
    owner = users.create("owner");
    orgId = api.createOrganization(owner);
    project = projects.createWithSource(owner, orgId, Map.of("package.json", "{}"));
    devEnvId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + project.id() + "/environments",
                "{\"type\":\"DEVELOPMENT\",\"branch\":\"develop\"}"),
            "$.id");
    when(github.putFile(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any()))
        .thenReturn("c0ffee0000000000000000000000000000000000");
  }

  @Test
  void previewShowsTheWorkflowAndRequiredSecretsWithoutWriting() {
    MvcTestResult preview = api.post(owner, pipelinePath() + "/preview", "{}");

    assertThat(preview).hasStatusOk();
    assertThat(preview).bodyJson().extractingPath("$.workflowPath").isEqualTo(WORKFLOW_PATH);
    assertThat(preview).bodyJson().extractingPath("$.branch").isEqualTo("develop");
    assertThat(preview).bodyJson().extractingPath("$.fileExists").isEqualTo(false);
    assertThat(read(preview, "$.content"))
        .contains("branches: [\"develop\"]")
        .contains("uses: actions/setup-node@v7")
        .contains("CLOUDFLOW_DEPLOY_TOKEN_DEVELOPMENT");
    assertThat(preview)
        .bodyJson()
        .extractingPath("$.secrets[0].value")
        .isEqualTo("https://cloudflow.example.test");
    verify(github, never())
        .putFile(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any());
  }

  @Test
  void commitWritesTheWorkflowAndTracksThePipeline() {
    MvcTestResult committed = api.post(owner, pipelinePath(), "{}");

    assertThat(committed).hasStatus(HttpStatus.CREATED);
    assertThat(committed)
        .bodyJson()
        .extractingPath("$.committedSha")
        .isEqualTo("c0ffee0000000000000000000000000000000000");
    assertThat(read(committed, "$.actionsUrl"))
        .endsWith("/actions/workflows/cloudflow-development.yml");
    verify(github)
        .putFile(
            anyString(),
            eq("octo"),
            eq(project.repositoryName()),
            eq(WORKFLOW_PATH),
            eq("develop"),
            eq("ci: add CloudFlow pipeline for development"),
            anyString(),
            isNull());

    // Regenerating updates the same file and pipeline.
    when(github.getFileSha(anyString(), anyString(), anyString(), eq(WORKFLOW_PATH), anyString()))
        .thenReturn("blobsha");
    assertThat(api.post(owner, pipelinePath(), "{}")).hasStatus(HttpStatus.CREATED);
    verify(github)
        .putFile(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq("ci: update CloudFlow pipeline for development"),
            anyString(),
            eq("blobsha"));
    assertThat(api.get(owner, "/api/v1/projects/" + project.id() + "/pipelines"))
        .bodyJson()
        .extractingPath("$.length()")
        .isEqualTo(1);
  }

  @Test
  void syncStoresRunsAndJobsFromTheActionsApi() {
    String pipelineId = read(api.post(owner, pipelinePath(), "{}"), "$.id");
    long runId = randomId();
    when(github.listWorkflowRuns(
            anyString(),
            eq("octo"),
            eq(project.repositoryName()),
            eq("cloudflow-development.yml"),
            anyInt()))
        .thenReturn(List.of(run(runId, "completed", "success")));
    when(github.listRunJobs(anyString(), anyString(), anyString(), eq(runId)))
        .thenReturn(
            List.of(
                job(randomId(), runId, "Test", "success"),
                job(randomId(), runId, "Deploy with CloudFlow", "success")));

    assertThat(api.post(owner, "/api/v1/pipelines/" + pipelineId + "/sync", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.latestRun.conclusion")
        .isEqualTo("success");
    MvcTestResult runs = api.get(owner, "/api/v1/pipelines/" + pipelineId + "/runs");
    assertThat(runs).bodyJson().extractingPath("$.content[0].runNumber").isEqualTo(7);
    assertThat(runs).bodyJson().extractingPath("$.content[0].commitMessage").isEqualTo("Ship it");
    assertThat(runs)
        .bodyJson()
        .extractingPath("$.content[0].jobs[*].name")
        .asArray()
        .contains("Test", "Deploy with CloudFlow");
  }

  @Test
  void deployTokensLetPipelinesDeployTheirEnvironmentOnly() {
    MvcTestResult created = createToken(owner, devEnvId);
    assertThat(created).hasStatus(HttpStatus.CREATED);
    assertThat(created)
        .bodyJson()
        .extractingPath("$.secretName")
        .isEqualTo("CLOUDFLOW_DEPLOY_TOKEN_DEVELOPMENT");
    String token = read(created, "$.token");
    assertThat(token).startsWith("cfd_").hasSize(44);
    assertThat(api.get(owner, "/api/v1/environments/" + devEnvId + "/deploy-tokens"))
        .bodyJson()
        .extractingPath("$[0].tokenPrefix")
        .isEqualTo(token.substring(0, 12));

    long runId = randomId();
    MvcTestResult deploy = hookDeploy(token, runId);
    assertThat(deploy).hasStatus(HttpStatus.ACCEPTED);
    String deploymentId = read(deploy, "$.id");
    Deployment deployment =
        deploymentRepository.findById(UUID.fromString(deploymentId)).orElseThrow();
    assertThat(deployment.getTriggerType()).isEqualTo(TriggerType.PIPELINE);
    assertThat(deployment.getPipelineRunId()).isEqualTo(runId);
    assertThat(deployment.getTriggeredBy()).isEqualTo(owner.id());

    assertThat(hookStatus(token, deploymentId)).hasStatusOk();
    assertThat(hookStatus("cfd_not-a-real-token", deploymentId)).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(hookDeploy(null, runId)).hasStatus(HttpStatus.UNAUTHORIZED);
    // "…" pasted into a secret arrives as UTF-8 bytes read as ISO-8859-1, including a control char.
    assertThat(hookDeploy("cfd_truncated\u00e2\u0080\u00a6", runId))
        .hasStatus(HttpStatus.BAD_REQUEST);

    // A token for another environment cannot see this deployment.
    String stagingId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + project.id() + "/environments",
                "{\"type\":\"STAGING\"}"),
            "$.id");
    String stagingToken = read(createToken(owner, stagingId), "$.token");
    assertThat(hookStatus(stagingToken, deploymentId)).hasStatus(HttpStatus.NOT_FOUND);

    String tokenId = read(created, "$.deployToken.id");
    assertThat(api.delete(owner, "/api/v1/deploy-tokens/" + tokenId))
        .hasStatus(HttpStatus.NO_CONTENT);
    assertThat(hookStatus(token, deploymentId)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokensStopWorkingWhenTheirCreatorLosesAccess() {
    TestUser developer = api.addMember(owner, orgId, "DEVELOPER");
    String token = read(createToken(developer, devEnvId), "$.token");

    assertThat(api.delete(owner, "/api/v1/organizations/" + orgId + "/members/" + developer.id()))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(hookDeploy(token, randomId())).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void enforcesPipelinePermissions() {
    TestUser viewer = api.addMember(owner, orgId, "VIEWER");
    TestUser developer = api.addMember(owner, orgId, "DEVELOPER");
    String productionId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + project.id() + "/environments",
                "{\"type\":\"PRODUCTION\"}"),
            "$.id");

    assertThat(api.post(viewer, pipelinePath() + "/preview", "{}")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(createToken(viewer, devEnvId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(createToken(developer, productionId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(api.get(viewer, "/api/v1/projects/" + project.id() + "/pipelines")).hasStatusOk();
  }

  @Test
  void signedWebhooksUpdateRuns() throws Exception {
    String pipelineId = read(api.post(owner, pipelinePath(), "{}"), "$.id");
    long runId = randomId();
    String payload =
        """
        {"action":"in_progress","repository":{"full_name":"octo/%s"},
         "workflow_run":{"id":%d,"name":"CloudFlow","path":"%s","workflow_id":99,
           "run_number":12,"run_attempt":1,"event":"push","status":"in_progress","conclusion":null,
           "head_branch":"develop","head_sha":"abc123abc123abc123abc123abc123abc123abcd",
           "head_commit":{"message":"Webhook run"},"actor":{"login":"octo"},
           "html_url":"https://github.com/octo/x/actions/runs/1",
           "run_started_at":"2026-09-25T10:00:00Z","updated_at":"2026-09-25T10:01:00Z"}}
        """
            .formatted(project.repositoryName(), runId, WORKFLOW_PATH);

    assertThat(webhook("workflow_run", payload, "sha256=" + "0".repeat(64)))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(webhook("workflow_run", payload, sign(payload))).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(api.get(owner, "/api/v1/pipelines/" + pipelineId))
        .bodyJson()
        .extractingPath("$.latestRun.status")
        .isEqualTo("in_progress");
    assertThat(webhook("ping", "{}", sign("{}"))).hasStatus(HttpStatus.ACCEPTED);
  }

  private String pipelinePath() {
    return "/api/v1/environments/" + devEnvId + "/pipeline";
  }

  private MvcTestResult createToken(TestUser user, String environmentId) {
    return api.post(
        user, "/api/v1/environments/" + environmentId + "/deploy-tokens", "{\"name\":\"ci\"}");
  }

  private MvcTestResult hookDeploy(String token, long runId) {
    var request =
        mvc.post()
            .uri("/api/v1/pipeline-hooks/deploy")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"commitSha\":\"" + TestProjects.HEAD_SHA + "\",\"runId\":" + runId + "}");
    if (token != null) {
      request = request.header(TOKEN_HEADER, token);
    }
    return request.exchange();
  }

  private MvcTestResult hookStatus(String token, String deploymentId) {
    return mvc.get()
        .uri("/api/v1/pipeline-hooks/deployments/" + deploymentId)
        .header(TOKEN_HEADER, token)
        .exchange();
  }

  private MvcTestResult webhook(String event, String payload, String signature) {
    return mvc.post()
        .uri("/api/v1/webhooks/github")
        .header("X-GitHub-Event", event)
        .header("X-Hub-Signature-256", signature)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload)
        .exchange();
  }

  private static String sign(String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256="
        + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }

  private static WorkflowRun run(long id, String status, String conclusion) {
    return new WorkflowRun(
        id,
        "CloudFlow",
        WORKFLOW_PATH,
        99,
        7,
        1,
        "push",
        status,
        conclusion,
        "develop",
        "abc123abc123abc123abc123abc123abc123abcd",
        new HeadCommit("Ship it\n\ndetails"),
        new Account("octo", null),
        "https://github.com/octo/x/actions/runs/" + id,
        Instant.parse("2026-09-25T10:00:00Z"),
        Instant.parse("2026-09-25T10:05:00Z"));
  }

  private static WorkflowJob job(long id, long runId, String name, String conclusion) {
    return new WorkflowJob(
        id,
        runId,
        name,
        "completed",
        conclusion,
        "https://github.com/octo/x/actions/runs/" + runId + "/job/" + id,
        Instant.parse("2026-09-25T10:00:00Z"),
        Instant.parse("2026-09-25T10:02:00Z"));
  }

  private static long randomId() {
    return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
  }
}
