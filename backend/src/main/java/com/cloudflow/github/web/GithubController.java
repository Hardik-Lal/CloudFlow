package com.cloudflow.github.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.github.dto.BranchResponse;
import com.cloudflow.github.dto.CommitResponse;
import com.cloudflow.github.dto.GithubRepositoryPage;
import com.cloudflow.github.dto.PullRequestResponse;
import com.cloudflow.github.service.GithubService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browses the caller's GitHub repositories, e.g. to pick one when creating a project. */
@Validated
@RestController
@RequestMapping("/api/v1/github/repositories")
public class GithubController {

  static final String NAME_PATTERN = "^[A-Za-z0-9_.-]{1,100}$";

  private final GithubService githubService;

  public GithubController(GithubService githubService) {
    this.githubService = githubService;
  }

  @GetMapping
  public GithubRepositoryPage listRepositories(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int perPage) {
    return githubService.listRepositories(user.id(), page, perPage);
  }

  @GetMapping("/{owner}/{repo}/branches")
  public List<BranchResponse> branches(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
      @PathVariable @Pattern(regexp = NAME_PATTERN) String repo) {
    return githubService.listBranches(user.id(), owner, repo).stream()
        .map(
            branch ->
                new BranchResponse(branch.name(), branch.commit().sha(), branch.isProtected()))
        .toList();
  }

  @GetMapping("/{owner}/{repo}/commits")
  public List<CommitResponse> commits(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
      @PathVariable @Pattern(regexp = NAME_PATTERN) String repo,
      @RequestParam @Pattern(regexp = "^[^\\s]{1,255}$") String branch) {
    return githubService.listCommits(user.id(), owner, repo, branch);
  }

  @GetMapping("/{owner}/{repo}/pulls")
  public List<PullRequestResponse> pulls(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
      @PathVariable @Pattern(regexp = NAME_PATTERN) String repo,
      @RequestParam(defaultValue = "open") @Pattern(regexp = "^(open|closed|all)$") String state) {
    return githubService.listPullRequests(user.id(), owner, repo, state);
  }
}
