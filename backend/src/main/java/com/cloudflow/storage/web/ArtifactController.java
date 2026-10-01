package com.cloudflow.storage.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.common.web.PageResponse;
import com.cloudflow.storage.domain.ArtifactKind;
import com.cloudflow.storage.dto.ArtifactResponse;
import com.cloudflow.storage.service.ArtifactService;
import com.cloudflow.storage.service.ArtifactService.ArtifactDownload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ArtifactController {

  private final ArtifactService artifactService;

  public ArtifactController(ArtifactService artifactService) {
    this.artifactService = artifactService;
  }

  @GetMapping("/projects/{projectId}/artifacts")
  public PageResponse<ArtifactResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID projectId,
      @RequestParam(required = false) ArtifactKind kind,
      @RequestParam(required = false) UUID deploymentId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return artifactService.list(
        projectId,
        user.id(),
        kind,
        deploymentId,
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  /** Downloads the artifact (always as an attachment, never rendered by the browser). */
  @GetMapping("/artifacts/{artifactId}/content")
  public ResponseEntity<InputStreamResource> content(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID artifactId) {
    ArtifactDownload download = artifactService.open(artifactId, user.id());
    InputStream content = download.content();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(download.artifact().sizeBytes())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(download.artifact().name()).build().toString())
        .body(new InputStreamResource(content));
  }
}
