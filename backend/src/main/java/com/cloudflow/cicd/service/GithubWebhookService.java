package com.cloudflow.cicd.service;

import com.cloudflow.cicd.config.CicdProperties;
import com.cloudflow.cicd.domain.Pipeline;
import com.cloudflow.cicd.repository.PipelineRepository;
import com.cloudflow.cicd.repository.PipelineRunRepository;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.common.exception.UnauthorizedException;
import com.cloudflow.github.client.GithubModels.WorkflowJob;
import com.cloudflow.github.client.GithubModels.WorkflowRun;
import com.cloudflow.project.service.ProjectLookupService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives GitHub {@code workflow_run} and {@code workflow_job} webhooks so pipeline status updates
 * in real time. Payloads are authenticated with the shared-secret HMAC signature GitHub sends in
 * {@code X-Hub-Signature-256}.
 */
@Service
public class GithubWebhookService {

  private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);
  private static final String SIGNATURE_PREFIX = "sha256=";

  private final CicdProperties properties;
  private final JsonMapper jsonMapper;
  private final ProjectLookupService projects;
  private final PipelineRepository pipelineRepository;
  private final PipelineRunRepository runRepository;
  private final PipelineRunSyncService syncService;

  public GithubWebhookService(
      CicdProperties properties,
      JsonMapper jsonMapper,
      ProjectLookupService projects,
      PipelineRepository pipelineRepository,
      PipelineRunRepository runRepository,
      PipelineRunSyncService syncService) {
    this.properties = properties;
    this.jsonMapper = jsonMapper;
    this.projects = projects;
    this.pipelineRepository = pipelineRepository;
    this.runRepository = runRepository;
    this.syncService = syncService;
  }

  /**
   * @return whether the event updated a tracked pipeline
   * @throws ResourceNotFoundException when webhooks are not configured
   * @throws UnauthorizedException when the signature is missing or wrong
   */
  public boolean handle(String event, byte[] payload, String signature) {
    if (!properties.webhooksEnabled()) {
      throw new ResourceNotFoundException("Webhook endpoint", "github");
    }
    verifySignature(payload, signature);
    return switch (event == null ? "" : event) {
      case "workflow_run" -> handleRun(jsonMapper.readValue(payload, WorkflowRunEvent.class));
      case "workflow_job" -> handleJob(jsonMapper.readValue(payload, WorkflowJobEvent.class));
      default -> false; // ping and unrelated events are acknowledged and ignored
    };
  }

  private boolean handleRun(WorkflowRunEvent event) {
    Optional<Pipeline> pipeline = findPipeline(event.repository(), event.workflowRun().path());
    pipeline.ifPresent(found -> syncService.upsertRun(found.getId(), event.workflowRun()));
    return pipeline.isPresent();
  }

  private boolean handleJob(WorkflowJobEvent event) {
    return runRepository
        .findByGithubRunId(event.workflowJob().runId())
        .map(
            run -> {
              syncService.upsertJob(run.getId(), event.workflowJob());
              return true;
            })
        .orElse(false);
  }

  private Optional<Pipeline> findPipeline(RepositoryRef repository, String workflowPath) {
    if (repository == null || workflowPath == null) {
      return Optional.empty();
    }
    List<UUID> projectIds = projects.findIdsByRepositoryFullName(repository.fullName());
    if (projectIds.isEmpty()) {
      return Optional.empty();
    }
    return pipelineRepository.findFirstByProjectIdInAndWorkflowPath(projectIds, workflowPath);
  }

  void verifySignature(byte[] payload, String signature) {
    if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
      throw new UnauthorizedException("Missing webhook signature");
    }
    byte[] expected = hmacSha256(payload);
    byte[] provided;
    try {
      provided = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
    } catch (IllegalArgumentException e) {
      throw new UnauthorizedException("Malformed webhook signature");
    }
    // Constant-time comparison to avoid leaking the expected signature through timing.
    if (!MessageDigest.isEqual(expected, provided)) {
      log.warn("Rejected GitHub webhook with an invalid signature");
      throw new UnauthorizedException("Invalid webhook signature");
    }
  }

  private byte[] hmacSha256(byte[] payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(payload);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
    }
  }

  record RepositoryRef(@JsonProperty("full_name") String fullName) {}

  record WorkflowRunEvent(
      String action,
      @JsonProperty("workflow_run") WorkflowRun workflowRun,
      RepositoryRef repository) {}

  record WorkflowJobEvent(
      String action,
      @JsonProperty("workflow_job") WorkflowJob workflowJob,
      RepositoryRef repository) {}
}
