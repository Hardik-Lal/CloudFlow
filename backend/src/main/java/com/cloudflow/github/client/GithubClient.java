package com.cloudflow.github.client;

import com.cloudflow.common.exception.ExternalServiceException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.github.client.GithubModels.Branch;
import com.cloudflow.github.client.GithubModels.Commit;
import com.cloudflow.github.client.GithubModels.ContentEntry;
import com.cloudflow.github.client.GithubModels.FileCommit;
import com.cloudflow.github.client.GithubModels.FileContent;
import com.cloudflow.github.client.GithubModels.FileInfo;
import com.cloudflow.github.client.GithubModels.PullRequest;
import com.cloudflow.github.client.GithubModels.Repository;
import com.cloudflow.github.client.GithubModels.WorkflowJob;
import com.cloudflow.github.client.GithubModels.WorkflowJobs;
import com.cloudflow.github.client.GithubModels.WorkflowRun;
import com.cloudflow.github.client.GithubModels.WorkflowRuns;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin client for the GitHub REST API. Every call is made with the calling user's OAuth token, so
 * GitHub's own permission model decides which repositories a user can see.
 */
@Component
public class GithubClient {

  static final String SERVICE = "GitHub";

  /** Upper bound on branches fetched per repository (3 pages of 100). */
  static final int MAX_BRANCH_PAGES = 3;

  private final RestClient restClient;

  public GithubClient(@Qualifier("githubRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  public GithubPage<Repository> listRepositories(String token, int page, int perPage) {
    ResponseEntity<List<Repository>> response =
        call(
            () ->
                restClient
                    .get()
                    .uri(
                        "/user/repos?sort=updated&affiliation=owner,collaborator,organization_member"
                            + "&page={page}&per_page={perPage}",
                        page,
                        perPage)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<Repository>>() {}),
            "repositories");
    return new GithubPage<>(bodyOrEmpty(response), page, perPage, hasNextPage(response));
  }

  public Repository getRepository(String token, String owner, String repo) {
    return call(
        () ->
            restClient
                .get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(Repository.class),
        owner + "/" + repo);
  }

  public List<Branch> listBranches(String token, String owner, String repo) {
    List<Branch> branches = new ArrayList<>();
    for (int page = 1; page <= MAX_BRANCH_PAGES; page++) {
      int currentPage = page;
      ResponseEntity<List<Branch>> response =
          call(
              () ->
                  restClient
                      .get()
                      .uri(
                          "/repos/{owner}/{repo}/branches?per_page=100&page={page}",
                          owner,
                          repo,
                          currentPage)
                      .headers(headers -> headers.setBearerAuth(token))
                      .retrieve()
                      .toEntity(new ParameterizedTypeReference<List<Branch>>() {}),
              owner + "/" + repo);
      branches.addAll(bodyOrEmpty(response));
      if (!hasNextPage(response)) {
        break;
      }
    }
    return branches;
  }

  public List<Commit> listCommits(
      String token, String owner, String repo, String branch, int perPage) {
    return call(
        () ->
            restClient
                .get()
                .uri(
                    "/repos/{owner}/{repo}/commits?sha={branch}&per_page={perPage}",
                    owner,
                    repo,
                    branch,
                    perPage)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Commit>>() {}),
        owner + "/" + repo + "@" + branch);
  }

  public List<PullRequest> listPullRequests(String token, String owner, String repo, String state) {
    return call(
        () ->
            restClient
                .get()
                .uri(
                    "/repos/{owner}/{repo}/pulls?state={state}&per_page=50&sort=updated"
                        + "&direction=desc",
                    owner,
                    repo,
                    state)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<List<PullRequest>>() {}),
        owner + "/" + repo);
  }

  /** Lists the files and directories at the repository root for the given ref. */
  public List<ContentEntry> listRootContents(String token, String owner, String repo, String ref) {
    return call(
        () ->
            restClient
                .get()
                .uri("/repos/{owner}/{repo}/contents?ref={ref}", owner, repo, ref)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ContentEntry>>() {}),
        owner + "/" + repo + "@" + ref);
  }

  /** Resolves a branch, tag, or SHA to its commit. */
  public Commit getCommit(String token, String owner, String repo, String ref) {
    return call(
        () ->
            restClient
                .get()
                .uri("/repos/{owner}/{repo}/commits/{ref}", owner, repo, ref)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(Commit.class),
        owner + "/" + repo + "@" + ref);
  }

  /**
   * Streams the repository contents at {@code sha} as a gzipped tarball into {@code target}. GitHub
   * answers with a redirect to a pre-signed codeload.github.com URL. The HTTP client does not
   * follow a cross-host redirect for a request carrying credentials, so it is followed here
   * explicitly, without the token: the pre-signed URL authorizes itself.
   */
  public void downloadTarball(String token, String owner, String repo, String sha, Path target) {
    call(
        () -> {
          URI location =
              restClient
                  .get()
                  .uri("/repos/{owner}/{repo}/tarball/{sha}", owner, repo, sha)
                  .headers(headers -> headers.setBearerAuth(token))
                  .exchange(
                      (request, response) -> {
                        if (response.getStatusCode().is3xxRedirection()
                            && response.getHeaders().getLocation() != null) {
                          return response.getHeaders().getLocation();
                        }
                        saveTarball(response, target);
                        return null;
                      });
          if (location != null) {
            restClient
                .get()
                .uri(location)
                .exchange(
                    (request, response) -> {
                      saveTarball(response, target);
                      return null;
                    });
          }
          return target;
        },
        owner + "/" + repo + "@" + sha);
  }

  private static void saveTarball(ConvertibleClientHttpResponse response, Path target)
      throws IOException {
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new RestClientResponseException(
          "Tarball download failed",
          response.getStatusCode(),
          response.getStatusText(),
          response.getHeaders(),
          null,
          null);
    }
    try (InputStream body = response.getBody()) {
      Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Text content of a file at {@code ref}, or {@code null} when it does not exist or is not a
   * regular file. Paths come from CloudFlow itself, never from user input.
   */
  public String getFileContent(String token, String owner, String repo, String path, String ref) {
    try {
      FileContent file =
          call(
              () ->
                  restClient
                      .get()
                      .uri(
                          "/repos/{owner}/{repo}/contents/" + path + "?ref={ref}", owner, repo, ref)
                      .headers(headers -> headers.setBearerAuth(token))
                      .retrieve()
                      .body(FileContent.class),
              owner + "/" + repo + ":" + path);
      if (!"file".equals(file.type())
          || !"base64".equals(file.encoding())
          || file.content() == null) {
        return null;
      }
      return new String(Base64.getMimeDecoder().decode(file.content()), StandardCharsets.UTF_8);
    } catch (ResourceNotFoundException e) {
      return null;
    }
  }

  /** Entries of a directory at {@code ref}; empty when it does not exist. */
  public List<ContentEntry> listDirectory(
      String token, String owner, String repo, String path, String ref) {
    try {
      return call(
          () ->
              restClient
                  .get()
                  .uri("/repos/{owner}/{repo}/contents/" + path + "?ref={ref}", owner, repo, ref)
                  .headers(headers -> headers.setBearerAuth(token))
                  .retrieve()
                  .body(new ParameterizedTypeReference<List<ContentEntry>>() {}),
          owner + "/" + repo + ":" + path);
    } catch (ResourceNotFoundException | RestClientException e) {
      // A path that is a file (not a directory) does not deserialize as a list.
      return List.of();
    }
  }

  /** The blob SHA of a file, or {@code null} if it does not exist on the branch. */
  public String getFileSha(String token, String owner, String repo, String path, String branch) {
    try {
      return call(
              () ->
                  restClient
                      .get()
                      .uri(
                          "/repos/{owner}/{repo}/contents/{path}?ref={branch}",
                          owner,
                          repo,
                          path,
                          branch)
                      .headers(headers -> headers.setBearerAuth(token))
                      .retrieve()
                      .body(FileInfo.class),
              owner + "/" + repo + ":" + path)
          .sha();
    } catch (ResourceNotFoundException e) {
      return null;
    }
  }

  /**
   * Creates or updates a file with a commit on {@code branch}.
   *
   * @param existingSha blob SHA of the current file when updating, otherwise {@code null}
   * @return the SHA of the new commit
   */
  public String putFile(
      String token,
      String owner,
      String repo,
      String path,
      String branch,
      String message,
      String content,
      String existingSha) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", message);
    body.put(
        "content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
    body.put("branch", branch);
    if (existingSha != null) {
      body.put("sha", existingSha);
    }
    return call(
            () ->
                restClient
                    .put()
                    .uri("/repos/{owner}/{repo}/contents/" + path, owner, repo)
                    .headers(headers -> headers.setBearerAuth(token))
                    .body(body)
                    .retrieve()
                    .body(FileCommit.class),
            owner + "/" + repo + ":" + path)
        .commit()
        .sha();
  }

  /** Recent runs of one workflow file, newest first. */
  public List<WorkflowRun> listWorkflowRuns(
      String token, String owner, String repo, String workflowFile, int perPage) {
    try {
      return call(
              () ->
                  restClient
                      .get()
                      .uri(
                          "/repos/{owner}/{repo}/actions/workflows/{file}/runs?per_page={perPage}",
                          owner,
                          repo,
                          workflowFile,
                          perPage)
                      .headers(headers -> headers.setBearerAuth(token))
                      .retrieve()
                      .body(WorkflowRuns.class),
              owner + "/" + repo + ":" + workflowFile)
          .workflowRuns();
    } catch (ResourceNotFoundException e) {
      // The workflow has not been registered by GitHub yet (no push since it was committed).
      return List.of();
    }
  }

  public List<WorkflowJob> listRunJobs(String token, String owner, String repo, long runId) {
    return call(
            () ->
                restClient
                    .get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{runId}/jobs", owner, repo, runId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(WorkflowJobs.class),
            owner + "/" + repo + " run " + runId)
        .jobs();
  }

  private static <T> T call(Supplier<T> request, String resource) {
    try {
      T result = request.get();
      if (result == null) {
        throw new ExternalServiceException(SERVICE, "GitHub returned an empty response");
      }
      return result;
    } catch (RestClientResponseException e) {
      throw translate(e, resource);
    } catch (RestClientException e) {
      throw new ExternalServiceException(SERVICE, "GitHub could not be reached", e);
    }
  }

  private static RuntimeException translate(RestClientResponseException e, String resource) {
    HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
    if (status == HttpStatus.NOT_FOUND) {
      // GitHub answers 404 for private repositories the token cannot see.
      return new ResourceNotFoundException("GitHub repository", resource);
    }
    if (status == HttpStatus.UNAUTHORIZED) {
      return new ExternalServiceException(
          SERVICE, "GitHub rejected the stored access token; sign in with GitHub again", e);
    }
    if (status == HttpStatus.FORBIDDEN || status == HttpStatus.TOO_MANY_REQUESTS) {
      return new ExternalServiceException(
          SERVICE, "GitHub denied the request (missing scope or rate limit exceeded)", e);
    }
    return new ExternalServiceException(
        SERVICE, "GitHub request failed with status " + e.getStatusCode().value(), e);
  }

  private static <T> List<T> bodyOrEmpty(ResponseEntity<List<T>> response) {
    return response.getBody() == null ? List.of() : response.getBody();
  }

  private static boolean hasNextPage(ResponseEntity<?> response) {
    String link = response.getHeaders().getFirst(HttpHeaders.LINK);
    return link != null && link.contains("rel=\"next\"");
  }
}
