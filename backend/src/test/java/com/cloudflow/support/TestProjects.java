package com.cloudflow.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.cloudflow.github.client.GithubClient;
import com.cloudflow.github.client.GithubModels.Account;
import com.cloudflow.github.client.GithubModels.Branch;
import com.cloudflow.github.client.GithubModels.Commit;
import com.cloudflow.github.client.GithubModels.CommitDetails;
import com.cloudflow.github.client.GithubModels.CommitRef;
import com.cloudflow.github.client.GithubModels.ContentEntry;
import com.cloudflow.github.client.GithubModels.GitActor;
import com.cloudflow.github.client.GithubModels.Repository;
import com.cloudflow.support.TestUsers.TestUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Creates projects through the API with the (mocked) GitHub client serving a fake repository: its
 * metadata, branches, head commit, and a source tarball with the given files.
 */
@TestComponent
public class TestProjects {

  public static final String HEAD_SHA = "0123456789abcdef0123456789abcdef01234567";

  private final GithubClient github;
  private final TestApi api;

  public TestProjects(GithubClient github, TestApi api) {
    this.github = github;
    this.api = api;
  }

  /**
   * @param rootFiles repository root files; they determine the detected application type
   * @return the project id
   */
  public String create(TestUser owner, String organizationId, List<String> rootFiles) {
    return createWithSource(
            owner,
            organizationId,
            rootFiles.stream().collect(Collectors.toMap(Function.identity(), file -> "")))
        .id();
  }

  /** Creates a project whose repository contains exactly {@code files}. */
  public TestProject createWithSource(
      TestUser owner, String organizationId, Map<String, String> files) {
    String name =
        "repo-" + Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), 36);
    when(github.getRepository(anyString(), eq("octo"), eq(name)))
        .thenReturn(
            new Repository(
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                name,
                "octo/" + name,
                new Account("octo", null),
                null,
                "https://github.com/octo/" + name,
                "https://github.com/octo/" + name + ".git",
                "main",
                false,
                null,
                Instant.now(),
                Instant.now()));
    when(github.listRootContents(anyString(), eq("octo"), eq(name), any()))
        .thenReturn(
            files.keySet().stream()
                .filter(file -> !file.contains("/"))
                .map(file -> new ContentEntry(file, file, "file"))
                .toList());
    when(github.listBranches(anyString(), eq("octo"), eq(name)))
        .thenReturn(
            List.of(
                new Branch("main", new CommitRef(HEAD_SHA), true),
                new Branch("develop", new CommitRef(HEAD_SHA), false)));
    when(github.getCommit(anyString(), eq("octo"), eq(name), anyString()))
        .thenReturn(
            new Commit(
                HEAD_SHA,
                new CommitDetails(
                    "Deployable change\n\nbody",
                    new GitActor("Octo", "o@example.com", Instant.now())),
                "https://github.com/octo/" + name + "/commit/" + HEAD_SHA,
                new Account("octo", null)));
    byte[] tarball = Tarballs.githubArchive("octo-" + name + "-0123456", files);
    doAnswer(
            invocation -> {
              Files.write(invocation.getArgument(4, Path.class), tarball);
              return null;
            })
        .when(github)
        .downloadTarball(anyString(), eq("octo"), eq(name), anyString(), any(Path.class));

    MvcTestResult result =
        api.post(
            owner,
            "/api/v1/organizations/" + organizationId + "/projects",
            "{\"repositoryFullName\":\"octo/" + name + "\"}");
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return new TestProject(TestApi.read(result, "$.id"), name);
  }

  public record TestProject(String id, String repositoryName) {}
}
