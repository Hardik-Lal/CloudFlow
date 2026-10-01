package com.cloudflow.github.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** Subsets of GitHub REST API payloads that CloudFlow reads. Unknown fields are ignored. */
public final class GithubModels {

  private GithubModels() {}

  public record Account(String login, @JsonProperty("avatar_url") String avatarUrl) {}

  public record Repository(
      long id,
      String name,
      @JsonProperty("full_name") String fullName,
      Account owner,
      String description,
      @JsonProperty("html_url") String htmlUrl,
      @JsonProperty("clone_url") String cloneUrl,
      @JsonProperty("default_branch") String defaultBranch,
      @JsonProperty("private") boolean isPrivate,
      String language,
      @JsonProperty("pushed_at") Instant pushedAt,
      @JsonProperty("updated_at") Instant updatedAt) {}

  public record CommitRef(String sha) {}

  public record Branch(
      String name, CommitRef commit, @JsonProperty("protected") boolean isProtected) {}

  public record GitActor(String name, String email, Instant date) {}

  public record CommitDetails(String message, GitActor author) {}

  public record Commit(
      String sha, CommitDetails commit, @JsonProperty("html_url") String htmlUrl, Account author) {}

  public record PullRequestRef(String ref, String sha) {}

  public record PullRequest(
      long number,
      String title,
      String state,
      boolean draft,
      @JsonProperty("html_url") String htmlUrl,
      Account user,
      PullRequestRef head,
      PullRequestRef base,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt) {}

  public record ContentEntry(String name, String path, String type) {}

  /** A file with its base64 content, as returned by the contents API. */
  public record FileContent(String path, String type, long size, String encoding, String content) {}

  /** A file from the contents API; only its blob SHA is needed to update it. */
  public record FileInfo(String sha, String path) {}

  public record CommitInfo(String sha) {}

  public record FileCommit(CommitInfo commit) {}

  public record WorkflowRun(
      long id,
      String name,
      String path,
      @JsonProperty("workflow_id") long workflowId,
      @JsonProperty("run_number") int runNumber,
      @JsonProperty("run_attempt") int runAttempt,
      String event,
      String status,
      String conclusion,
      @JsonProperty("head_branch") String headBranch,
      @JsonProperty("head_sha") String headSha,
      @JsonProperty("head_commit") HeadCommit headCommit,
      Account actor,
      @JsonProperty("html_url") String htmlUrl,
      @JsonProperty("run_started_at") Instant runStartedAt,
      @JsonProperty("updated_at") Instant updatedAt) {}

  public record HeadCommit(String message) {}

  public record WorkflowRuns(@JsonProperty("workflow_runs") List<WorkflowRun> workflowRuns) {}

  public record WorkflowJob(
      long id,
      @JsonProperty("run_id") long runId,
      String name,
      String status,
      String conclusion,
      @JsonProperty("html_url") String htmlUrl,
      @JsonProperty("started_at") Instant startedAt,
      @JsonProperty("completed_at") Instant completedAt) {}

  public record WorkflowJobs(List<WorkflowJob> jobs) {}
}
