package com.cloudflow.assistant.service;

import com.cloudflow.assistant.client.AiModels.KnowledgeDocument;
import com.cloudflow.common.web.PageResponse;
import com.cloudflow.deployment.domain.DeploymentLog;
import com.cloudflow.deployment.dto.DeploymentResponse;
import com.cloudflow.deployment.service.DeploymentQueryService;
import com.cloudflow.deployment.service.DeploymentService;
import com.cloudflow.environment.dto.DeploymentConfigResponse;
import com.cloudflow.environment.dto.EnvironmentResponse;
import com.cloudflow.environment.dto.VariableResponse;
import com.cloudflow.environment.service.EnvironmentService;
import com.cloudflow.environment.service.VariableService;
import com.cloudflow.github.client.GithubModels.ContentEntry;
import com.cloudflow.github.service.GithubService;
import com.cloudflow.monitoring.dto.EnvironmentEventResponse;
import com.cloudflow.monitoring.service.MonitoringService;
import com.cloudflow.project.dto.ProjectSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Collects project knowledge for the AI knowledge base: repository documentation and configuration
 * files, environment configuration, deployment history, failure log excerpts, and recent events.
 * Secret values never leave CloudFlow; only the fact that a secret exists.
 */
@Component
public class KnowledgeCollector {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeCollector.class);

  static final int MAX_FILE_BYTES = 100_000;
  static final int MAX_FILES = 40;
  static final int DEPLOYMENT_HISTORY = 20;
  static final int FAILURE_LOG_LINES = 80;

  private static final Pattern ROOT_FILES =
      Pattern.compile(
          "(?i)^(readme(\\..+)?|dockerfile.*|.*\\.dockerfile|docker-compose.*\\.ya?ml|compose\\.ya?ml"
              + "|package\\.json|requirements.*\\.txt|pyproject\\.toml|pom\\.xml|build\\.gradle(\\.kts)?"
              + "|settings\\.gradle(\\.kts)?|\\.env\\.example|procfile|makefile|app\\.py|main\\.py)$");

  private final GithubService github;
  private final EnvironmentService environments;
  private final VariableService variables;
  private final DeploymentService deployments;
  private final DeploymentQueryService deploymentQueries;
  private final MonitoringService monitoring;

  public KnowledgeCollector(
      GithubService github,
      EnvironmentService environments,
      VariableService variables,
      DeploymentService deployments,
      DeploymentQueryService deploymentQueries,
      MonitoringService monitoring) {
    this.github = github;
    this.environments = environments;
    this.variables = variables;
    this.deployments = deployments;
    this.deploymentQueries = deploymentQueries;
    this.monitoring = monitoring;
  }

  /** Everything CloudFlow knows about the project, as seen by {@code userId}. */
  public List<KnowledgeDocument> collect(ProjectSnapshot project, UUID userId) {
    List<KnowledgeDocument> documents = new ArrayList<>(repositoryFiles(project, userId));
    for (EnvironmentResponse environment : environments.list(project.id(), userId)) {
      documents.add(environmentDocument(environment, userId));
      List<EnvironmentEventResponse> events = monitoring.events(environment.id(), userId, 50);
      if (!events.isEmpty()) {
        documents.add(eventsDocument(environment, events));
      }
    }
    PageResponse<DeploymentResponse> history =
        deployments.listForProject(
            project.id(),
            userId,
            PageRequest.of(0, DEPLOYMENT_HISTORY, Sort.by(Sort.Direction.DESC, "createdAt")));
    for (DeploymentResponse deployment : history.content()) {
      if (!deployment.status().isInProgress()) {
        documents.add(deploymentDocument(deployment));
      }
    }
    return documents;
  }

  /** The record and failure excerpt of one finished deployment (no user context needed). */
  public KnowledgeDocument deploymentDocument(DeploymentResponse deployment) {
    StringBuilder content = new StringBuilder();
    content
        .append("Deployment ")
        .append(deployment.id())
        .append("\nStatus: ")
        .append(deployment.status())
        .append("\nTrigger: ")
        .append(deployment.triggerType())
        .append("\nBranch: ")
        .append(deployment.branch())
        .append("\nCommit: ")
        .append(deployment.commitSha())
        .append(deployment.commitMessage() == null ? "" : " (" + deployment.commitMessage() + ")")
        .append("\nDetected application type: ")
        .append(deployment.appType())
        .append("\nImage: ")
        .append(deployment.imageTag())
        .append("\nStarted: ")
        .append(deployment.startedAt())
        .append("\nFinished: ")
        .append(deployment.finishedAt());
    if (deployment.failureReason() != null) {
      content.append("\nFailure reason: ").append(deployment.failureReason());
    }
    if (deployment.rollbackOfId() != null) {
      content.append("\nRollback to deployment: ").append(deployment.rollbackOfId());
    }
    if (deployment.failureReason() != null) {
      List<DeploymentLog> tail = deploymentQueries.logTail(deployment.id(), FAILURE_LOG_LINES);
      content.append("\n\nLast ").append(tail.size()).append(" log lines:\n");
      // Log lines were masked for secrets when they were stored.
      tail.forEach(
          line ->
              content
                  .append('[')
                  .append(line.getPhase())
                  .append("] ")
                  .append(line.getLevel())
                  .append(' ')
                  .append(line.getMessage())
                  .append('\n'));
    }
    return new KnowledgeDocument(
        deployment.failureReason() == null ? "DEPLOYMENT" : "LOG",
        "deployment:" + deployment.id(),
        deployment.environmentId(),
        deployment.id(),
        content.toString(),
        Map.of("status", deployment.status().name()));
  }

  private List<KnowledgeDocument> repositoryFiles(ProjectSnapshot project, UUID userId) {
    String owner = project.repositoryOwner();
    String repo = project.repositoryName();
    String ref = project.defaultBranch();
    Set<String> paths = new LinkedHashSet<>();
    try {
      github.listRootFileNames(userId, owner, repo, ref).stream()
          .filter(name -> ROOT_FILES.matcher(name).matches())
          .sorted()
          .forEach(paths::add);
      addDirectory(paths, userId, project, "docs", "(?i).+\\.(md|txt)$");
      addDirectory(paths, userId, project, ".github/workflows", "(?i).+\\.ya?ml$");
      addDirectory(
          paths, userId, project, "src/main/resources", "(?i)application.*\\.(ya?ml|properties)$");
    } catch (RuntimeException e) {
      log.warn(
          "Could not list repository files of {}: {}",
          project.repositoryFullName(),
          e.getMessage());
      return List.of();
    }

    List<KnowledgeDocument> documents = new ArrayList<>();
    for (String path : paths.stream().limit(MAX_FILES).toList()) {
      String content = github.getFileContent(userId, owner, repo, path, ref);
      if (content == null || content.isBlank() || content.length() > MAX_FILE_BYTES) {
        continue;
      }
      documents.add(
          new KnowledgeDocument(
              sourceTypeOf(path),
              "file:" + path,
              null,
              null,
              content,
              Map.of("path", path, "ref", ref)));
    }
    return documents;
  }

  private void addDirectory(
      Set<String> paths,
      UUID userId,
      ProjectSnapshot project,
      String directory,
      String namePattern) {
    for (ContentEntry entry :
        github.listDirectory(
            userId,
            project.repositoryOwner(),
            project.repositoryName(),
            directory,
            project.defaultBranch())) {
      if ("file".equals(entry.type()) && entry.name().matches(namePattern)) {
        paths.add(entry.path());
      }
    }
  }

  private KnowledgeDocument environmentDocument(EnvironmentResponse environment, UUID userId) {
    StringBuilder content =
        new StringBuilder()
            .append("Environment: ")
            .append(environment.type())
            .append("\nDeployed branch: ")
            .append(environment.branch());
    DeploymentConfigResponse config = environment.config();
    if (config != null) {
      content
          .append("\nDeployment configuration:")
          .append("\n  template: ")
          .append(config.template())
          .append("\n  runtime version: ")
          .append(config.runtimeVersion())
          .append("\n  build command: ")
          .append(config.buildCommand())
          .append("\n  start command: ")
          .append(config.startCommand())
          .append("\n  Dockerfile path: ")
          .append(config.dockerfilePath())
          .append("\n  container port: ")
          .append(config.containerPort())
          .append("\n  health-check path: ")
          .append(config.healthCheckPath())
          .append("\n  CPU limit: ")
          .append(config.cpuLimit())
          .append("\n  memory limit (MB): ")
          .append(config.memoryLimitMb());
    }
    content.append("\nEnvironment variables:");
    for (VariableResponse variable : variables.list(environment.id(), userId)) {
      content
          .append("\n  ")
          .append(variable.key())
          .append(variable.secret() ? " = <secret, value hidden>" : " = " + variable.value());
    }
    return new KnowledgeDocument(
        "ENVIRONMENT",
        "environment:" + environment.type().name().toLowerCase(Locale.ROOT),
        environment.id(),
        null,
        content.toString(),
        Map.of("environment", environment.type().name()));
  }

  private KnowledgeDocument eventsDocument(
      EnvironmentResponse environment, List<EnvironmentEventResponse> events) {
    StringBuilder content =
        new StringBuilder("Recent events of the ")
            .append(environment.type())
            .append(" environment (newest first):");
    events.forEach(
        event ->
            content
                .append("\n")
                .append(event.createdAt())
                .append(' ')
                .append(event.severity())
                .append(' ')
                .append(event.type())
                .append(": ")
                .append(event.message()));
    return new KnowledgeDocument(
        "LOG",
        "events:" + environment.type().name().toLowerCase(Locale.ROOT),
        environment.id(),
        null,
        content.toString(),
        Map.of("environment", environment.type().name()));
  }

  static String sourceTypeOf(String path) {
    String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    if (name.startsWith("readme")) {
      return "README";
    }
    if (name.contains("dockerfile")) {
      return "DOCKERFILE";
    }
    if (name.contains("compose")) {
      return "COMPOSE";
    }
    if (path.startsWith(".github/workflows/")) {
      return "WORKFLOW";
    }
    if (path.startsWith("docs/")) {
      return "DOCUMENTATION";
    }
    if (name.matches(
        "package\\.json|requirements.*|pyproject\\.toml|pom\\.xml|build\\.gradle.*|settings\\.gradle.*")) {
      return "BUILD_MANIFEST";
    }
    return "CONFIGURATION";
  }
}
