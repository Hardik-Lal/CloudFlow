package com.cloudflow.project.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.github.dto.BranchResponse;
import com.cloudflow.github.dto.CommitResponse;
import com.cloudflow.github.dto.PullRequestResponse;
import com.cloudflow.project.dto.CreateProjectRequest;
import com.cloudflow.project.dto.ProjectResponse;
import com.cloudflow.project.dto.ProjectSummaryResponse;
import com.cloudflow.project.dto.UpdateProjectRequest;
import com.cloudflow.project.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ProjectController {

  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  @GetMapping("/organizations/{organizationId}/projects")
  public List<ProjectSummaryResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID organizationId) {
    return projectService.list(organizationId, user.id());
  }

  @PostMapping("/organizations/{organizationId}/projects")
  public ResponseEntity<ProjectResponse> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID organizationId,
      @Valid @RequestBody CreateProjectRequest request) {
    ProjectResponse project = projectService.create(organizationId, user.id(), request);
    return ResponseEntity.created(URI.create("/api/v1/projects/" + project.id())).body(project);
  }

  @GetMapping("/projects/{projectId}")
  public ProjectResponse get(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    return projectService.get(projectId, user.id());
  }

  @PatchMapping("/projects/{projectId}")
  public ProjectResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody UpdateProjectRequest request) {
    return projectService.update(projectId, user.id(), request);
  }

  @DeleteMapping("/projects/{projectId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    projectService.delete(projectId, user.id());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/projects/{projectId}/sync")
  public ProjectResponse sync(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    return projectService.sync(projectId, user.id());
  }

  @GetMapping("/projects/{projectId}/branches")
  public List<BranchResponse> branches(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    return projectService.branches(projectId, user.id());
  }

  @GetMapping("/projects/{projectId}/commits")
  public List<CommitResponse> commits(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID projectId,
      @RequestParam(required = false) @Pattern(regexp = "^[^\\s]{1,255}$") String branch) {
    return projectService.commits(projectId, user.id(), branch);
  }

  @GetMapping("/projects/{projectId}/pulls")
  public List<PullRequestResponse> pullRequests(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID projectId,
      @RequestParam(defaultValue = "open") @Pattern(regexp = "^(open|closed|all)$") String state) {
    return projectService.pullRequests(projectId, user.id(), state);
  }
}
