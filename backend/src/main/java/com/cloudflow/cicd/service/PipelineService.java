package com.cloudflow.cicd.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.cicd.config.CicdProperties;
import com.cloudflow.cicd.domain.Pipeline;
import com.cloudflow.cicd.domain.PipelineRun;
import com.cloudflow.cicd.domain.PipelineRunJob;
import com.cloudflow.cicd.dto.PipelineJobResponse;
import com.cloudflow.cicd.dto.PipelinePreviewResponse;
import com.cloudflow.cicd.dto.PipelineResponse;
import com.cloudflow.cicd.dto.PipelineRunResponse;
import com.cloudflow.cicd.dto.RequiredSecret;
import com.cloudflow.cicd.repository.PipelineRepository;
import com.cloudflow.cicd.repository.PipelineRunJobRepository;
import com.cloudflow.cicd.repository.PipelineRunRepository;
import com.cloudflow.cicd.service.PipelineRunSyncService.UpsertedRun;
import com.cloudflow.cicd.service.WorkflowGenerator.WorkflowSpec;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.common.web.PageResponse;
import com.cloudflow.deployment.engine.DockerfileGenerator;
import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.domain.EnvironmentType;
import com.cloudflow.environment.dto.EnvironmentSnapshot;
import com.cloudflow.environment.service.DeploymentConfigService;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.environment.service.EnvironmentLookupService;
import com.cloudflow.github.client.GithubModels.WorkflowRun;
import com.cloudflow.github.service.GithubService;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.project.dto.ProjectSnapshot;
import com.cloudflow.project.service.ProjectAccessService;
import com.cloudflow.project.service.ProjectLookupService;
import com.cloudflow.storage.domain.ArtifactKind;
import com.cloudflow.storage.service.ArtifactService;
import com.cloudflow.storage.service.ArtifactUpload;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates, commits (after user approval), and tracks GitHub Actions pipelines. GitHub calls
 * happen outside database transactions.
 */
@Service
public class PipelineService {

  private final ArtifactService artifacts;

  private final AuditLogger audit;

  private final PipelineRepository pipelineRepository;
  private final PipelineRunRepository runRepository;
  private final PipelineRunJobRepository jobRepository;
  private final PipelineRunSyncService syncService;
  private final WorkflowGenerator workflowGenerator;
  private final DockerfileGenerator dockerfileGenerator;
  private final EnvironmentAccessService environmentAccess;
  private final EnvironmentLookupService environments;
  private final DeploymentConfigService configs;
  private final ProjectAccessService projectAccess;
  private final ProjectLookupService projects;
  private final GithubService githubService;
  private final TransactionTemplate transactionTemplate;
  private final CicdProperties properties;

  public PipelineService(
      PipelineRepository pipelineRepository,
      PipelineRunRepository runRepository,
      PipelineRunJobRepository jobRepository,
      PipelineRunSyncService syncService,
      WorkflowGenerator workflowGenerator,
      DockerfileGenerator dockerfileGenerator,
      EnvironmentAccessService environmentAccess,
      EnvironmentLookupService environments,
      DeploymentConfigService configs,
      ProjectAccessService projectAccess,
      ProjectLookupService projects,
      GithubService githubService,
      TransactionTemplate transactionTemplate,
      CicdProperties properties,
      AuditLogger audit,
      ArtifactService artifacts) {
    this.artifacts = artifacts;
    this.audit = audit;
    this.pipelineRepository = pipelineRepository;
    this.runRepository = runRepository;
    this.jobRepository = jobRepository;
    this.syncService = syncService;
    this.workflowGenerator = workflowGenerator;
    this.dockerfileGenerator = dockerfileGenerator;
    this.environmentAccess = environmentAccess;
    this.environments = environments;
    this.configs = configs;
    this.projectAccess = projectAccess;
    this.projects = projects;
    this.githubService = githubService;
    this.transactionTemplate = transactionTemplate;
    this.properties = properties;
  }

  /** Renders the workflow for an environment without writing anything. */
  public PipelinePreviewResponse preview(UUID environmentId, UUID userId) {
    Generated generated = generate(environmentId, userId);
    String existingSha =
        githubService.getFileSha(
            userId,
            generated.project().repositoryOwner(),
            generated.project().repositoryName(),
            generated.path(),
            generated.environment().branch());
    return new PipelinePreviewResponse(
        environmentId,
        generated.path(),
        generated.environment().branch(),
        generated.content(),
        existingSha != null,
        requiredSecrets(generated.environment().type()));
  }

  /**
   * Commits the generated workflow to the environment's branch (creating or replacing it) and
   * starts tracking it. Called only after the user approved the preview.
   */
  public PipelineResponse commit(UUID environmentId, UUID userId) {
    Generated generated = generate(environmentId, userId);
    ProjectSnapshot project = generated.project();
    EnvironmentSnapshot environment = generated.environment();
    String existingSha =
        githubService.getFileSha(
            userId,
            project.repositoryOwner(),
            project.repositoryName(),
            generated.path(),
            environment.branch());
    String commitSha =
        githubService.commitFile(
            userId,
            project.repositoryOwner(),
            project.repositoryName(),
            generated.path(),
            environment.branch(),
            (existingSha == null ? "ci: add" : "ci: update")
                + " CloudFlow pipeline for "
                + environment.type().name().toLowerCase(Locale.ROOT),
            generated.content(),
            existingSha);

    artifacts.storeAsync(
        () ->
            ArtifactUpload.text(
                project.id(),
                environmentId,
                null,
                ArtifactKind.GENERATED_FILE,
                generated.path(),
                "text/yaml; charset=utf-8",
                generated.content(),
                userId));

    Pipeline pipeline =
        transactionTemplate.execute(
            status -> {
              Pipeline stored =
                  pipelineRepository
                      .findByEnvironmentId(environmentId)
                      .orElseGet(
                          () ->
                              new Pipeline(
                                  project.id(),
                                  environmentId,
                                  generated.path(),
                                  generated.template(),
                                  userId));
              stored.recordCommit(commitSha, generated.template(), userId);
              Pipeline saved = pipelineRepository.save(stored);
              audit.record(
                  project.organizationId(),
                  userId,
                  AuditAction.PIPELINE_COMMITTED,
                  "pipeline",
                  saved.getId(),
                  Map.of(
                      "workflowPath", generated.path(),
                      "branch", environment.branch(),
                      "commitSha", commitSha));
              return saved;
            });
    return toResponse(pipeline, project, environment, latestRuns(List.of(pipeline)));
  }

  @Transactional(readOnly = true)
  public List<PipelineResponse> list(UUID projectId, UUID userId) {
    projectAccess.requirePermission(projectId, userId, Permission.PIPELINE_READ);
    ProjectSnapshot project = projects.snapshot(projectId);
    List<Pipeline> pipelines = pipelineRepository.findAllByProjectId(projectId);
    Map<UUID, PipelineRunResponse> latest = latestRuns(pipelines);
    return pipelines.stream()
        .map(
            pipeline ->
                toResponse(
                    pipeline, project, environments.snapshot(pipeline.getEnvironmentId()), latest))
        .sorted(Comparator.comparing(PipelineResponse::environmentType))
        .toList();
  }

  @Transactional(readOnly = true)
  public PipelineResponse get(UUID pipelineId, UUID userId) {
    Pipeline pipeline = authorizedPipeline(pipelineId, userId);
    return toResponse(
        pipeline,
        projects.snapshot(pipeline.getProjectId()),
        environments.snapshot(pipeline.getEnvironmentId()),
        latestRuns(List.of(pipeline)));
  }

  @Transactional(readOnly = true)
  public PageResponse<PipelineRunResponse> runs(UUID pipelineId, UUID userId, Pageable pageable) {
    authorizedPipeline(pipelineId, userId);
    Page<PipelineRun> runs = runRepository.findAllByPipelineId(pipelineId, pageable);
    Map<UUID, List<PipelineJobResponse>> jobs = jobsByRun(runs.getContent());
    return PageResponse.of(
        runs, run -> PipelineRunResponse.from(run, jobs.getOrDefault(run.getId(), List.of())));
  }

  /** Pulls recent runs and their jobs from the GitHub Actions API. */
  public PipelineResponse sync(UUID pipelineId, UUID userId) {
    Pipeline pipeline =
        transactionTemplate.execute(status -> authorizedPipeline(pipelineId, userId));
    ProjectSnapshot project = projects.snapshot(pipeline.getProjectId());
    List<WorkflowRun> runs =
        githubService.listWorkflowRuns(
            userId,
            project.repositoryOwner(),
            project.repositoryName(),
            pipeline.workflowFileName(),
            properties.runsPerSync());
    for (WorkflowRun run : runs) {
      UpsertedRun upserted = syncService.upsertRun(pipeline.getId(), run);
      if (upserted.refreshJobs()) {
        githubService
            .listRunJobs(userId, project.repositoryOwner(), project.repositoryName(), run.id())
            .forEach(job -> syncService.upsertJob(upserted.runId(), job));
      }
    }
    syncService.markSynced(pipeline);
    return transactionTemplate.execute(status -> get(pipelineId, userId));
  }

  /** Stops tracking the pipeline. The workflow file stays in the repository. */
  @Transactional
  public void delete(UUID pipelineId, UUID userId) {
    Pipeline pipeline = pipelineRepository.findById(pipelineId).orElse(null);
    if (pipeline == null) {
      throw new ResourceNotFoundException("Pipeline", pipelineId);
    }
    requireWrite(pipeline.getProjectId(), userId, pipelineId);
    pipelineRepository.delete(pipeline);
    audit.record(
        projects.snapshot(pipeline.getProjectId()).organizationId(),
        userId,
        AuditAction.PIPELINE_DELETED,
        "pipeline",
        pipelineId,
        Map.of("workflowPath", pipeline.getWorkflowPath()));
  }

  private Generated generate(UUID environmentId, UUID userId) {
    EnvironmentSnapshot environment =
        transactionTemplate.execute(
            status -> {
              environmentAccess.require(
                  environmentId, userId, Permission.PIPELINE_WRITE, Permission.PIPELINE_WRITE);
              return environments.snapshot(environmentId);
            });
    ProjectSnapshot project = projects.snapshot(environment.projectId());
    DeploymentSettings settings = configs.settings(environmentId);
    String dockerfile =
        settings.template() == ConfigTemplate.DOCKER
            ? null
            : dockerfileGenerator.generate(settings, project.appType());
    String content =
        workflowGenerator.generate(
            new WorkflowSpec(
                project.slug(),
                environment.type(),
                environment.branch(),
                settings,
                project.appType(),
                dockerfile));
    return new Generated(
        project,
        environment,
        WorkflowGenerator.workflowPath(environment.type()),
        settings.template(),
        content);
  }

  private List<RequiredSecret> requiredSecrets(EnvironmentType type) {
    return List.of(
        new RequiredSecret(
            "CLOUDFLOW_URL",
            "CloudFlow API URL reachable from GitHub Actions runners",
            properties.publicApiUrl()),
        new RequiredSecret(
            WorkflowGenerator.deployTokenSecretName(type),
            "A deploy token for this environment (create one below; it is shown only once)",
            null));
  }

  private Pipeline authorizedPipeline(UUID pipelineId, UUID userId) {
    Pipeline pipeline =
        pipelineRepository
            .findById(pipelineId)
            .orElseThrow(() -> new ResourceNotFoundException("Pipeline", pipelineId));
    try {
      projectAccess.requirePermission(pipeline.getProjectId(), userId, Permission.PIPELINE_READ);
    } catch (ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Pipeline", pipelineId);
    }
    return pipeline;
  }

  private void requireWrite(UUID projectId, UUID userId, UUID pipelineId) {
    try {
      projectAccess.requirePermission(projectId, userId, Permission.PIPELINE_WRITE);
    } catch (ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Pipeline", pipelineId);
    }
  }

  private Map<UUID, PipelineRunResponse> latestRuns(List<Pipeline> pipelines) {
    if (pipelines.isEmpty()) {
      return Map.of();
    }
    List<PipelineRun> runs =
        runRepository.findLatestRuns(pipelines.stream().map(Pipeline::getId).toList());
    Map<UUID, List<PipelineJobResponse>> jobs = jobsByRun(runs);
    return runs.stream()
        .collect(
            Collectors.toMap(
                PipelineRun::getPipelineId,
                run -> PipelineRunResponse.from(run, jobs.getOrDefault(run.getId(), List.of())),
                (first, second) -> first));
  }

  private Map<UUID, List<PipelineJobResponse>> jobsByRun(List<PipelineRun> runs) {
    if (runs.isEmpty()) {
      return Map.of();
    }
    return jobRepository
        .findAllByRunIdInOrderByStartedAtAsc(runs.stream().map(PipelineRun::getId).toList())
        .stream()
        .collect(
            Collectors.groupingBy(
                PipelineRunJob::getRunId,
                Collectors.mapping(PipelineJobResponse::from, Collectors.toList())));
  }

  private static PipelineResponse toResponse(
      Pipeline pipeline,
      ProjectSnapshot project,
      EnvironmentSnapshot environment,
      Map<UUID, PipelineRunResponse> latestRuns) {
    String repositoryUrl = "https://github.com/" + project.repositoryFullName();
    return new PipelineResponse(
        pipeline.getId(),
        pipeline.getProjectId(),
        pipeline.getEnvironmentId(),
        environment.type(),
        environment.branch(),
        pipeline.getWorkflowPath(),
        pipeline.getTemplate(),
        pipeline.getCommittedSha(),
        repositoryUrl + "/blob/" + environment.branch() + "/" + pipeline.getWorkflowPath(),
        repositoryUrl + "/actions/workflows/" + pipeline.workflowFileName(),
        pipeline.getLastSyncedAt(),
        latestRuns.get(pipeline.getId()),
        pipeline.getCreatedAt());
  }

  private record Generated(
      ProjectSnapshot project,
      EnvironmentSnapshot environment,
      String path,
      ConfigTemplate template,
      String content) {}
}
