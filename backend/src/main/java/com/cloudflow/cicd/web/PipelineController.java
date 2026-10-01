package com.cloudflow.cicd.web;

import com.cloudflow.cicd.dto.PipelinePreviewResponse;
import com.cloudflow.cicd.dto.PipelineResponse;
import com.cloudflow.cicd.dto.PipelineRunResponse;
import com.cloudflow.cicd.service.PipelineService;
import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class PipelineController {

  private final PipelineService pipelineService;

  public PipelineController(PipelineService pipelineService) {
    this.pipelineService = pipelineService;
  }

  @GetMapping("/projects/{projectId}/pipelines")
  public List<PipelineResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    return pipelineService.list(projectId, user.id());
  }

  /** Renders the workflow for review; nothing is written to the repository. */
  @PostMapping("/environments/{environmentId}/pipeline/preview")
  public PipelinePreviewResponse preview(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    return pipelineService.preview(environmentId, user.id());
  }

  /** Commits the approved workflow to the environment's branch. */
  @PostMapping("/environments/{environmentId}/pipeline")
  public ResponseEntity<PipelineResponse> commit(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
    PipelineResponse pipeline = pipelineService.commit(environmentId, user.id());
    return ResponseEntity.created(URI.create("/api/v1/pipelines/" + pipeline.id())).body(pipeline);
  }

  @GetMapping("/pipelines/{pipelineId}")
  public PipelineResponse get(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID pipelineId) {
    return pipelineService.get(pipelineId, user.id());
  }

  @GetMapping("/pipelines/{pipelineId}/runs")
  public PageResponse<PipelineRunResponse> runs(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID pipelineId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return pipelineService.runs(
        pipelineId,
        user.id(),
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "runNumber")));
  }

  @PostMapping("/pipelines/{pipelineId}/sync")
  public PipelineResponse sync(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID pipelineId) {
    return pipelineService.sync(pipelineId, user.id());
  }

  @DeleteMapping("/pipelines/{pipelineId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID pipelineId) {
    pipelineService.delete(pipelineId, user.id());
    return ResponseEntity.noContent().build();
  }
}
