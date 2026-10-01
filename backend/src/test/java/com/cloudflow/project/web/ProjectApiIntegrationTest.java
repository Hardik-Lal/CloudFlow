package com.cloudflow.project.web;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudflow.common.exception.ExternalServiceException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.github.client.GithubClient;
import com.cloudflow.github.client.GithubModels.Account;
import com.cloudflow.github.client.GithubModels.Branch;
import com.cloudflow.github.client.GithubModels.Commit;
import com.cloudflow.github.client.GithubModels.CommitDetails;
import com.cloudflow.github.client.GithubModels.CommitRef;
import com.cloudflow.github.client.GithubModels.ContentEntry;
import com.cloudflow.github.client.GithubModels.GitActor;
import com.cloudflow.github.client.GithubModels.Repository;
import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class ProjectApiIntegrationTest {

  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private GithubClient github;

  private TestUser owner;
  private String orgId;
  private long repoId;

  @BeforeEach
  void setUp() {
    owner = users.create("owner");
    orgId = api.createOrganization(owner);
    repoId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    stubRepository("demo-service", "main", List.of("pom.xml", "src"));
    stubBranches(List.of("main", "develop"));
  }

  @Test
  void createsProjectFromRepositoryWithDetectedTypeAndBranches() {
    MvcTestResult created = createProject("{\"repositoryFullName\":\"octo/demo-service\"}");

    assertThat(created).hasStatus(HttpStatus.CREATED);
    assertThat(created).bodyJson().extractingPath("$.name").isEqualTo("demo-service");
    assertThat(created).bodyJson().extractingPath("$.slug").isEqualTo("demo-service");
    assertThat(created).bodyJson().extractingPath("$.appType").isEqualTo("JAVA_MAVEN");
    assertThat(created)
        .bodyJson()
        .extractingPath("$.repository.fullName")
        .isEqualTo("octo/demo-service");
    assertThat(created).bodyJson().extractingPath("$.repository.isPrivate").isEqualTo(true);
    assertThat(created).bodyJson().extractingPath("$.role").isEqualTo("OWNER");

    String projectId = read(created, "$.id");
    assertThat(api.get(owner, "/api/v1/projects/" + projectId + "/branches"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].name")
        .asArray()
        .containsExactly("develop", "main");
    assertThat(api.get(owner, "/api/v1/organizations/" + orgId + "/projects"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].id")
        .asArray()
        .containsExactly(projectId);
  }

  @Test
  void rejectsLinkingTheSameRepositoryTwiceInAnOrganization() {
    assertThat(createProject("{\"repositoryFullName\":\"octo/demo-service\"}"))
        .hasStatus(HttpStatus.CREATED);

    assertThat(
            createProject(
                "{\"repositoryFullName\":\"octo/demo-service\",\"slug\":\"another-slug\"}"))
        .hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void validatesRepositoryName() {
    assertThat(createProject("{\"repositoryFullName\":\"not a repo\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("repositoryFullName");
  }

  @Test
  void repositoriesInvisibleOnGithubAreNotFound() {
    when(github.getRepository(anyString(), eq("octo"), eq("private-repo")))
        .thenThrow(new ResourceNotFoundException("GitHub repository", "octo/private-repo"));

    assertThat(createProject("{\"repositoryFullName\":\"octo/private-repo\"}"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void githubOutagesSurfaceAsBadGateway() {
    when(github.getRepository(anyString(), anyString(), anyString()))
        .thenThrow(new ExternalServiceException("GitHub", "GitHub could not be reached"));

    assertThat(createProject("{\"repositoryFullName\":\"octo/demo-service\"}"))
        .hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:cloudflow:problem:upstream-error");
  }

  @Test
  void enforcesRbac() {
    String projectId =
        read(createProject("{\"repositoryFullName\":\"octo/demo-service\"}"), "$.id");
    TestUser viewer = api.addMember(owner, orgId, "VIEWER");
    TestUser developer = api.addMember(owner, orgId, "DEVELOPER");
    TestUser outsider = users.create("outsider");

    assertThat(api.get(viewer, "/api/v1/projects/" + projectId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.permissions")
        .asArray()
        .doesNotContain("PROJECT_WRITE");
    assertThat(api.patch(viewer, "/api/v1/projects/" + projectId, "{\"name\":\"x\"}"))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(api.patch(developer, "/api/v1/projects/" + projectId, "{\"name\":\"Renamed\"}"))
        .hasStatusOk();
    assertThat(api.delete(developer, "/api/v1/projects/" + projectId))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(api.get(outsider, "/api/v1/projects/" + projectId)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(api.get(outsider, "/api/v1/organizations/" + orgId + "/projects"))
        .hasStatus(HttpStatus.NOT_FOUND);

    assertThat(api.delete(owner, "/api/v1/projects/" + projectId)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(api.get(owner, "/api/v1/projects/" + projectId)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void syncRefreshesBranchesAndApplicationType() {
    String projectId =
        read(createProject("{\"repositoryFullName\":\"octo/demo-service\"}"), "$.id");
    stubRepository("demo-service", "trunk", List.of("Dockerfile", "pom.xml"));
    stubBranches(List.of("trunk", "feature-x"));

    assertThat(api.post(owner, "/api/v1/projects/" + projectId + "/sync", "{}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.appType")
        .isEqualTo("DOCKERFILE");
    assertThat(api.get(owner, "/api/v1/projects/" + projectId + "/branches"))
        .bodyJson()
        .extractingPath("$[*].name")
        .asArray()
        .containsExactly("feature-x", "trunk");
  }

  @Test
  void commitsDefaultToTheRepositoryDefaultBranch() {
    String projectId =
        read(createProject("{\"repositoryFullName\":\"octo/demo-service\"}"), "$.id");
    when(github.listCommits(anyString(), eq("octo"), eq("demo-service"), eq("main"), anyInt()))
        .thenReturn(
            List.of(
                new Commit(
                    "0123456789abcdef",
                    new CommitDetails(
                        "Add feature\n\nLong body",
                        new GitActor("Octo Cat", "o@example.com", Instant.now())),
                    "https://github.com/octo/demo-service/commit/0123456",
                    new Account("octocat", null))));

    assertThat(api.get(owner, "/api/v1/projects/" + projectId + "/commits"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[0].message")
        .isEqualTo("Add feature");
    verify(github).listCommits(anyString(), eq("octo"), eq("demo-service"), eq("main"), anyInt());
  }

  private MvcTestResult createProject(String json) {
    return api.post(owner, "/api/v1/organizations/" + orgId + "/projects", json);
  }

  private void stubRepository(String name, String defaultBranch, List<String> rootFiles) {
    when(github.getRepository(anyString(), eq("octo"), eq(name)))
        .thenReturn(
            new Repository(
                repoId,
                name,
                "octo/" + name,
                new Account("octo", null),
                "Demo",
                "https://github.com/octo/" + name,
                "https://github.com/octo/" + name + ".git",
                defaultBranch,
                true,
                "Java",
                Instant.now(),
                Instant.now()));
    when(github.listRootContents(anyString(), eq("octo"), eq(name), any()))
        .thenReturn(rootFiles.stream().map(file -> new ContentEntry(file, file, "file")).toList());
  }

  private void stubBranches(List<String> names) {
    when(github.listBranches(anyString(), eq("octo"), anyString()))
        .thenReturn(
            names.stream().map(n -> new Branch(n, new CommitRef("sha-" + n), false)).toList());
  }
}
