package com.cloudflow.github.service;

import com.cloudflow.github.client.GithubClient;
import com.cloudflow.github.client.GithubModels.Branch;
import com.cloudflow.github.client.GithubModels.Commit;
import com.cloudflow.github.client.GithubModels.ContentEntry;
import com.cloudflow.github.client.GithubModels.Repository;
import com.cloudflow.github.client.GithubModels.WorkflowJob;
import com.cloudflow.github.client.GithubModels.WorkflowRun;
import com.cloudflow.github.client.GithubPage;
import com.cloudflow.github.dto.CommitResponse;
import com.cloudflow.github.dto.GithubRepositoryPage;
import com.cloudflow.github.dto.GithubRepositoryResponse;
import com.cloudflow.github.dto.PullRequestResponse;
import com.cloudflow.user.service.GithubCredentialService;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** GitHub operations performed on behalf of a CloudFlow user, using their stored OAuth token. */
@Service
public class GithubService {

  private static final int MAX_PER_PAGE = 100;
  private static final int COMMITS_PER_PAGE = 20;

  private final GithubClient githubClient;
  private final GithubCredentialService credentialService;

  public GithubService(GithubClient githubClient, GithubCredentialService credentialService) {
    this.githubClient = githubClient;
    this.credentialService = credentialService;
  }

  public GithubRepositoryPage listRepositories(UUID userId, int page, int perPage) {
    GithubPage<Repository> result =
        githubClient.listRepositories(
            token(userId), Math.max(page, 1), Math.clamp(perPage, 1, MAX_PER_PAGE));
    return new GithubRepositoryPage(
        result.items().stream().map(GithubRepositoryResponse::from).toList(),
        result.page(),
        result.perPage(),
        result.hasNext());
  }

  public Repository getRepository(UUID userId, String owner, String repo) {
    return githubClient.getRepository(token(userId), owner, repo);
  }

  public List<Branch> listBranches(UUID userId, String owner, String repo) {
    return githubClient.listBranches(token(userId), owner, repo);
  }

  public List<CommitResponse> listCommits(UUID userId, String owner, String repo, String branch) {
    return githubClient.listCommits(token(userId), owner, repo, branch, COMMITS_PER_PAGE).stream()
        .map(CommitResponse::from)
        .toList();
  }

  public List<PullRequestResponse> listPullRequests(
      UUID userId, String owner, String repo, String state) {
    return githubClient.listPullRequests(token(userId), owner, repo, state).stream()
        .map(PullRequestResponse::from)
        .toList();
  }

  public Commit resolveCommit(UUID userId, String owner, String repo, String ref) {
    return githubClient.getCommit(token(userId), owner, repo, ref);
  }

  public void downloadSource(UUID userId, String owner, String repo, String sha, Path target) {
    githubClient.downloadTarball(token(userId), owner, repo, sha, target);
  }

  public String getFileContent(UUID userId, String owner, String repo, String path, String ref) {
    return githubClient.getFileContent(token(userId), owner, repo, path, ref);
  }

  public List<ContentEntry> listDirectory(
      UUID userId, String owner, String repo, String path, String ref) {
    return githubClient.listDirectory(token(userId), owner, repo, path, ref);
  }

  public String getFileSha(UUID userId, String owner, String repo, String path, String branch) {
    return githubClient.getFileSha(token(userId), owner, repo, path, branch);
  }

  public String commitFile(
      UUID userId,
      String owner,
      String repo,
      String path,
      String branch,
      String message,
      String content,
      String existingSha) {
    return githubClient.putFile(
        token(userId), owner, repo, path, branch, message, content, existingSha);
  }

  public List<WorkflowRun> listWorkflowRuns(
      UUID userId, String owner, String repo, String workflowFile, int perPage) {
    return githubClient.listWorkflowRuns(token(userId), owner, repo, workflowFile, perPage);
  }

  public List<WorkflowJob> listRunJobs(UUID userId, String owner, String repo, long runId) {
    return githubClient.listRunJobs(token(userId), owner, repo, runId);
  }

  /** Names of the files and directories at the repository root. */
  public Set<String> listRootFileNames(UUID userId, String owner, String repo, String ref) {
    return githubClient.listRootContents(token(userId), owner, repo, ref).stream()
        .map(entry -> entry.name())
        .collect(Collectors.toSet());
  }

  private String token(UUID userId) {
    return credentialService.getAccessToken(userId);
  }
}
