package com.cloudflow.assistant.service;

import com.cloudflow.assistant.client.AiModels.AnalysisRequest;
import com.cloudflow.assistant.client.AiModels.Answer;
import com.cloudflow.assistant.client.AiModels.QueryRequest;
import com.cloudflow.assistant.client.AiServiceClient;
import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.dto.DeploymentLogResponse;
import com.cloudflow.deployment.dto.DeploymentResponse;
import com.cloudflow.deployment.engine.DockerfileGenerator;
import com.cloudflow.deployment.service.DeploymentQueryService;
import com.cloudflow.deployment.service.DeploymentService;
import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.dto.EnvironmentResponse;
import com.cloudflow.environment.dto.VariableResponse;
import com.cloudflow.environment.service.DeploymentConfigService;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.environment.service.EnvironmentService;
import com.cloudflow.environment.service.VariableService;
import com.cloudflow.github.service.GithubService;
import com.cloudflow.monitoring.dto.EnvironmentMetricsResponse;
import com.cloudflow.monitoring.service.MonitoringService;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.project.dto.ProjectSnapshot;
import com.cloudflow.project.service.ProjectAccessService;
import com.cloudflow.project.service.ProjectLookupService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Assembles project-specific evidence for AI answers: live platform state for questions, and for
 * deployment analysis the deployment, its logs, configuration (secret values removed), Dockerfile,
 * and the previous successful deployment.
 */
@Service
public class AssistantService {

  static final int ANALYSIS_TAIL_LINES = 200;
  static final int ANALYSIS_MAX_LINES = 400;

  private final AiServiceClient ai;
  private final ProjectAccessService projectAccess;
  private final ProjectLookupService projects;
  private final EnvironmentAccessService environmentAccess;
  private final EnvironmentService environments;
  private final VariableService variables;
  private final DeploymentConfigService configs;
  private final DeploymentService deployments;
  private final DeploymentQueryService deploymentQueries;
  private final MonitoringService monitoring;
  private final DockerfileGenerator dockerfileGenerator;
  private final GithubService github;

  public AssistantService(
      AiServiceClient ai,
      ProjectAccessService projectAccess,
      ProjectLookupService projects,
      EnvironmentAccessService environmentAccess,
      EnvironmentService environments,
      VariableService variables,
      DeploymentConfigService configs,
      DeploymentService deployments,
      DeploymentQueryService deploymentQueries,
      MonitoringService monitoring,
      DockerfileGenerator dockerfileGenerator,
      GithubService github) {
    this.ai = ai;
    this.projectAccess = projectAccess;
    this.projects = projects;
    this.environmentAccess = environmentAccess;
    this.environments = environments;
    this.variables = variables;
    this.configs = configs;
    this.deployments = deployments;
    this.deploymentQueries = deploymentQueries;
    this.monitoring = monitoring;
    this.dockerfileGenerator = dockerfileGenerator;
    this.github = github;
  }

  public Answer ask(UUID projectId, UUID userId, String question, UUID environmentId) {
    projectAccess.requirePermission(projectId, userId, Permission.AI_USE);
    ProjectSnapshot project = projects.snapshot(projectId);
    Map<String, Object> liveContext = new LinkedHashMap<>();
    liveContext.put(
        "project",
        Map.of(
            "name", project.name(),
            "repository", project.repositoryFullName(),
            "defaultBranch", project.defaultBranch(),
            "detectedAppType", project.appType().name()));
    List<Map<String, Object>> environmentStates = new ArrayList<>();
    for (EnvironmentResponse environment : environments.list(projectId, userId)) {
      if (environmentId == null || environment.id().equals(environmentId)) {
        environmentStates.add(environmentState(environment, userId));
      }
    }
    liveContext.put("environments", environmentStates);
    return ai.query(new QueryRequest(projectId, environmentId, question.strip(), liveContext));
  }

  public Answer analyzeDeployment(UUID deploymentId, UUID userId) {
    UUID environmentId = deployments.authorizeRead(deploymentId, userId);
    environmentAccess.require(environmentId, userId, Permission.AI_USE, Permission.AI_USE);
    DeploymentResponse deployment = deployments.get(deploymentId, userId);
    ProjectSnapshot project = projects.snapshot(deployment.projectId());
    DeploymentSettings settings = configs.settings(environmentId);

    Map<String, Object> previous =
        deploymentQueries
            .previousSuccessful(environmentId, deployment.createdAt())
            .map(AssistantService::summarize)
            .orElse(null);
    return ai.analyzeDeployment(
        new AnalysisRequest(
            deployment.projectId(),
            environmentId,
            summarize(deployment),
            selectLogLines(deployments.logs(deploymentId, userId, 0, 1000)),
            configuration(settings, variables.list(environmentId, userId)),
            previous,
            dockerfile(project, settings, deployment, userId)));
  }

  private Map<String, Object> environmentState(EnvironmentResponse environment, UUID userId) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", environment.id());
    state.put("type", environment.type());
    state.put("branch", environment.branch());
    if (environment.config() != null) {
      state.put("template", environment.config().template());
      state.put("containerPort", environment.config().containerPort());
      state.put("healthCheckPath", environment.config().healthCheckPath());
    }
    state.put(
        "recentDeployments",
        deployments
            .listForEnvironment(
                environment.id(),
                userId,
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "createdAt")))
            .content()
            .stream()
            .map(AssistantService::summarize)
            .toList());
    EnvironmentMetricsResponse metrics = monitoring.metrics(environment.id(), userId);
    state.put("serviceStatus", metrics.status());
    state.put("health", metrics.health());
    state.put(
        "recentEvents",
        monitoring.events(environment.id(), userId, 10).stream()
            .map(event -> event.createdAt() + " " + event.severity() + " " + event.message())
            .toList());
    return state;
  }

  /** All warnings and errors plus the latest lines, in order, capped. */
  static List<String> selectLogLines(List<DeploymentLogResponse> lines) {
    int tailStart = Math.max(0, lines.size() - ANALYSIS_TAIL_LINES);
    List<String> selected = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      DeploymentLogResponse line = lines.get(i);
      boolean important = !"INFO".equals(line.level().name());
      if (i >= tailStart || important) {
        selected.add("[" + line.phase() + "] " + line.level() + " " + line.message());
      }
    }
    return selected.size() > ANALYSIS_MAX_LINES
        ? selected.subList(selected.size() - ANALYSIS_MAX_LINES, selected.size())
        : selected;
  }

  /** Deployment configuration with secret values replaced by a marker. */
  static Map<String, Object> configuration(
      DeploymentSettings settings, List<VariableResponse> environmentVariables) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put("template", settings.template());
    configuration.put("runtimeVersion", settings.runtimeVersion());
    configuration.put("buildCommand", settings.buildCommand());
    configuration.put("startCommand", settings.startCommand());
    configuration.put("dockerfilePath", settings.dockerfilePath());
    configuration.put("containerPort", settings.containerPort());
    configuration.put("healthCheckPath", settings.healthCheckPath());
    configuration.put("cpuLimit", settings.cpuLimit());
    configuration.put("memoryLimitMb", settings.memoryLimitMb());
    Map<String, String> variableView = new LinkedHashMap<>();
    environmentVariables.forEach(
        variable ->
            variableView.put(
                variable.key(), variable.secret() ? "<secret, value hidden>" : variable.value()));
    configuration.put("variables", variableView);
    return configuration;
  }

  private String dockerfile(
      ProjectSnapshot project,
      DeploymentSettings settings,
      DeploymentResponse deployment,
      UUID userId) {
    if (settings.template() == ConfigTemplate.DOCKER) {
      String ref = deployment.commitSha() != null ? deployment.commitSha() : deployment.branch();
      try {
        return github.getFileContent(
            userId,
            project.repositoryOwner(),
            project.repositoryName(),
            settings.dockerfilePath(),
            ref);
      } catch (RuntimeException e) {
        return null;
      }
    }
    return dockerfileGenerator.generate(
        settings, deployment.appType() != null ? deployment.appType() : project.appType());
  }

  private static Map<String, Object> summarize(DeploymentResponse deployment) {
    Map<String, Object> summary = new LinkedHashMap<>();
    putIfPresent(summary, "id", deployment.id());
    putIfPresent(summary, "status", deployment.status());
    putIfPresent(summary, "trigger", deployment.triggerType());
    putIfPresent(summary, "branch", deployment.branch());
    putIfPresent(summary, "commitSha", deployment.commitSha());
    putIfPresent(summary, "commitMessage", deployment.commitMessage());
    putIfPresent(summary, "appType", deployment.appType());
    putIfPresent(summary, "imageTag", deployment.imageTag());
    putIfPresent(summary, "failureReason", deployment.failureReason());
    putIfPresent(summary, "startedAt", deployment.startedAt());
    putIfPresent(summary, "finishedAt", deployment.finishedAt());
    return summary;
  }

  private static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private static Map<String, Object> summarize(Deployment deployment) {
    Map<String, Object> summary = new LinkedHashMap<>();
    putIfPresent(summary, "id", deployment.getId());
    putIfPresent(summary, "status", deployment.getStatus());
    putIfPresent(summary, "commitSha", deployment.getCommitSha());
    putIfPresent(summary, "commitMessage", deployment.getCommitMessage());
    putIfPresent(summary, "imageTag", deployment.getImageTag());
    putIfPresent(summary, "finishedAt", deployment.getFinishedAt());
    return summary;
  }
}
