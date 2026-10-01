package com.cloudflow.assistant.service;

import com.cloudflow.assistant.client.AiModels.GeneratedArtifact;
import com.cloudflow.assistant.client.AiModels.GenerationRequest;
import com.cloudflow.assistant.client.AiServiceClient;
import com.cloudflow.assistant.domain.AiSuggestion;
import com.cloudflow.assistant.domain.SuggestionStatus;
import com.cloudflow.assistant.domain.SuggestionType;
import com.cloudflow.assistant.dto.GenerateSuggestionRequest;
import com.cloudflow.assistant.dto.SuggestionResponse;
import com.cloudflow.assistant.repository.AiSuggestionRepository;
import com.cloudflow.assistant.service.EnvTemplateParser.TemplateVariable;
import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.common.exception.ConflictException;
import com.cloudflow.common.exception.InvalidRequestException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.dto.EnvironmentRef;
import com.cloudflow.environment.dto.EnvironmentSnapshot;
import com.cloudflow.environment.dto.PutVariableRequest;
import com.cloudflow.environment.dto.VariableResponse;
import com.cloudflow.environment.service.DeploymentConfigService;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.environment.service.EnvironmentLookupService;
import com.cloudflow.environment.service.VariableService;
import com.cloudflow.github.service.GithubService;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.project.dto.ProjectSnapshot;
import com.cloudflow.project.service.ProjectAccessService;
import com.cloudflow.project.service.ProjectLookupService;
import com.cloudflow.storage.domain.ArtifactKind;
import com.cloudflow.storage.service.ArtifactService;
import com.cloudflow.storage.service.ArtifactUpload;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI-generated Dockerfiles, environment templates, workflows, and documentation. Generation only
 * stores a PENDING suggestion; nothing changes until a user with {@code AI_APPLY} (and the
 * permission the change itself needs) applies it.
 */
@Service
public class SuggestionService {

  private final ArtifactService artifacts;

  private final AuditLogger audit;

  private static final int MAX_CONTEXT_FILE = 20_000;
  private static final Set<String> CONTEXT_FILES =
      Set.of(
          "README.md",
          "package.json",
          "requirements.txt",
          "pyproject.toml",
          "pom.xml",
          "build.gradle",
          "build.gradle.kts",
          "Dockerfile",
          "docker-compose.yml",
          "compose.yml");

  private final AiServiceClient ai;
  private final AiSuggestionRepository repository;
  private final ProjectAccessService projectAccess;
  private final ProjectLookupService projects;
  private final EnvironmentAccessService environmentAccess;
  private final EnvironmentLookupService environments;
  private final DeploymentConfigService configs;
  private final VariableService variables;
  private final GithubService github;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public SuggestionService(
      AiServiceClient ai,
      AiSuggestionRepository repository,
      ProjectAccessService projectAccess,
      ProjectLookupService projects,
      EnvironmentAccessService environmentAccess,
      EnvironmentLookupService environments,
      DeploymentConfigService configs,
      VariableService variables,
      GithubService github,
      TransactionTemplate transactions,
      Clock clock,
      AuditLogger audit,
      ArtifactService artifacts) {
    this.artifacts = artifacts;
    this.audit = audit;
    this.ai = ai;
    this.repository = repository;
    this.projectAccess = projectAccess;
    this.projects = projects;
    this.environmentAccess = environmentAccess;
    this.environments = environments;
    this.configs = configs;
    this.variables = variables;
    this.github = github;
    this.transactions = transactions;
    this.clock = clock;
  }

  public SuggestionResponse generate(
      UUID projectId, UUID userId, GenerateSuggestionRequest request) {
    projectAccess.requirePermission(projectId, userId, Permission.AI_USE);
    if (request.type() == SuggestionType.ENV_TEMPLATE && request.environmentId() == null) {
      throw new InvalidRequestException(
          "environmentId", "An environment is required for environment templates");
    }
    if (request.environmentId() != null) {
      requireEnvironmentOfProject(request.environmentId(), projectId, userId);
    }
    ProjectSnapshot project = projects.snapshot(projectId);
    GeneratedArtifact artifact =
        ai.generate(
            request.type().name(),
            new GenerationRequest(
                projectId,
                request.environmentId(),
                context(project, request.environmentId(), userId),
                blankToNull(request.instructions())));
    AiSuggestion suggestion =
        transactions.execute(
            status ->
                repository.save(
                    new AiSuggestion(
                        projectId,
                        request.environmentId(),
                        request.type(),
                        SuggestionPaths.resolve(request.type(), artifact.filePath()),
                        artifact.content(),
                        artifact.explanation(),
                        blankToNull(request.instructions()),
                        userId)));
    return SuggestionResponse.from(suggestion);
  }

  public List<SuggestionResponse> list(UUID projectId, UUID userId) {
    projectAccess.requirePermission(projectId, userId, Permission.PROJECT_READ);
    return repository.findTop50ByProjectIdOrderByCreatedAtDesc(projectId).stream()
        .map(SuggestionResponse::from)
        .toList();
  }

  /** Applies an approved suggestion: commits the file, or creates the template's variables. */
  public SuggestionResponse apply(UUID suggestionId, UUID userId) {
    AiSuggestion suggestion = loadPending(suggestionId, userId, Permission.AI_APPLY);
    String result =
        suggestion.getType() == SuggestionType.ENV_TEMPLATE
            ? applyEnvironmentTemplate(suggestion, userId)
            : commitFile(suggestion, userId);
    return transactions.execute(
        status -> {
          AiSuggestion current = load(suggestionId);
          current.markApplied(userId, clock.instant(), result);
          auditReview(current, userId, AuditAction.AI_SUGGESTION_APPLIED);
          return SuggestionResponse.from(repository.save(current));
        });
  }

  public SuggestionResponse reject(UUID suggestionId, UUID userId) {
    loadPending(suggestionId, userId, Permission.AI_USE);
    return transactions.execute(
        status -> {
          AiSuggestion current = load(suggestionId);
          current.markRejected(userId, clock.instant());
          auditReview(current, userId, AuditAction.AI_SUGGESTION_REJECTED);
          return SuggestionResponse.from(repository.save(current));
        });
  }

  private String commitFile(AiSuggestion suggestion, UUID userId) {
    projectAccess.requirePermission(suggestion.getProjectId(), userId, Permission.PROJECT_WRITE);
    if (suggestion.getType() == SuggestionType.WORKFLOW) {
      projectAccess.requirePermission(suggestion.getProjectId(), userId, Permission.PIPELINE_WRITE);
    }
    ProjectSnapshot project = projects.snapshot(suggestion.getProjectId());
    String branch =
        suggestion.getEnvironmentId() != null
            ? environments.snapshot(suggestion.getEnvironmentId()).branch()
            : project.defaultBranch();
    String existingSha =
        github.getFileSha(
            userId,
            project.repositoryOwner(),
            project.repositoryName(),
            suggestion.getFilePath(),
            branch);
    String commitSha =
        github.commitFile(
            userId,
            project.repositoryOwner(),
            project.repositoryName(),
            suggestion.getFilePath(),
            branch,
            "chore: "
                + (existingSha == null ? "add " : "update ")
                + suggestion.getFilePath()
                + " (CloudFlow AI suggestion, approved)",
            suggestion.getContent().endsWith("\n")
                ? suggestion.getContent()
                : suggestion.getContent() + "\n",
            existingSha);
    artifacts.storeAsync(
        () ->
            ArtifactUpload.text(
                suggestion.getProjectId(),
                suggestion.getEnvironmentId(),
                null,
                ArtifactKind.GENERATED_FILE,
                suggestion.getFilePath(),
                "text/plain; charset=utf-8",
                suggestion.getContent(),
                userId));
    return "Committed "
        + suggestion.getFilePath()
        + " to "
        + branch
        + " ("
        + commitSha.substring(0, 7)
        + ")";
  }

  private String applyEnvironmentTemplate(AiSuggestion suggestion, UUID userId) {
    UUID environmentId = suggestion.getEnvironmentId();
    // Writing variables enforces ENV_WRITE / ENV_WRITE_PRODUCTION for this user.
    environmentAccess.requireWrite(environmentId, userId);
    Set<String> existing =
        variables.list(environmentId, userId).stream()
            .map(VariableResponse::key)
            .collect(Collectors.toSet());
    List<String> created = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    List<String> secretsToSet = new ArrayList<>();
    for (TemplateVariable variable : EnvTemplateParser.parse(suggestion.getContent())) {
      if (existing.contains(variable.key())) {
        skipped.add(variable.key());
      } else if (variable.secret()) {
        // Placeholder secret values would break deployments silently; the user sets them.
        secretsToSet.add(variable.key());
      } else {
        variables.put(
            environmentId, userId, variable.key(), new PutVariableRequest(variable.value(), false));
        created.add(variable.key());
      }
    }
    StringBuilder result =
        new StringBuilder("Created ").append(created.size()).append(" variable(s)");
    if (!created.isEmpty()) {
      result.append(": ").append(String.join(", ", created));
    }
    if (!skipped.isEmpty()) {
      result.append(". Kept existing: ").append(String.join(", ", skipped));
    }
    if (!secretsToSet.isEmpty()) {
      result.append(". Set these secrets yourself: ").append(String.join(", ", secretsToSet));
    }
    return result.toString();
  }

  private Map<String, Object> context(ProjectSnapshot project, UUID environmentId, UUID userId) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("projectName", project.name());
    context.put("repository", project.repositoryFullName());
    context.put("defaultBranch", project.defaultBranch());
    context.put("detectedAppType", project.appType().name());
    if (environmentId != null) {
      EnvironmentSnapshot environment = environments.snapshot(environmentId);
      DeploymentSettings settings = configs.settings(environmentId);
      context.put("environment", environment.type().name().toLowerCase(Locale.ROOT));
      context.put("branch", environment.branch());
      context.put("deploymentConfiguration", settings);
      context.put(
          "existingVariables",
          variables.list(environmentId, userId).stream().map(VariableResponse::key).toList());
    }
    try {
      List<String> rootFiles =
          github
              .listRootFileNames(
                  userId,
                  project.repositoryOwner(),
                  project.repositoryName(),
                  project.defaultBranch())
              .stream()
              .sorted()
              .toList();
      context.put("repositoryRootFiles", rootFiles);
      Map<String, String> files = new LinkedHashMap<>();
      for (String name : rootFiles) {
        if (CONTEXT_FILES.contains(name)) {
          String content =
              github.getFileContent(
                  userId,
                  project.repositoryOwner(),
                  project.repositoryName(),
                  name,
                  project.defaultBranch());
          if (content != null) {
            files.put(
                name,
                content.length() > MAX_CONTEXT_FILE
                    ? content.substring(0, MAX_CONTEXT_FILE)
                    : content);
          }
        }
      }
      context.put("repositoryFiles", files);
    } catch (RuntimeException e) {
      context.put("repositoryFiles", Map.of());
    }
    return context;
  }

  private void requireEnvironmentOfProject(UUID environmentId, UUID projectId, UUID userId) {
    EnvironmentRef environment = environmentAccess.requireRead(environmentId, userId);
    if (!environment.projectId().equals(projectId)) {
      throw new ResourceNotFoundException("Environment", environmentId);
    }
  }

  private void auditReview(AiSuggestion suggestion, UUID userId, AuditAction action) {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("type", suggestion.getType().name());
    details.put("filePath", suggestion.getFilePath());
    if (suggestion.getResult() != null) {
      details.put("result", suggestion.getResult());
    }
    audit.record(
        projects.snapshot(suggestion.getProjectId()).organizationId(),
        userId,
        action,
        "ai-suggestion",
        suggestion.getId(),
        details);
  }

  private AiSuggestion loadPending(UUID suggestionId, UUID userId, Permission permission) {
    AiSuggestion suggestion = load(suggestionId);
    try {
      projectAccess.requirePermission(suggestion.getProjectId(), userId, permission);
    } catch (ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Suggestion", suggestionId);
    }
    if (suggestion.getStatus() != SuggestionStatus.PENDING) {
      throw new ConflictException(
          "This suggestion was already " + suggestion.getStatus().name().toLowerCase(Locale.ROOT));
    }
    return suggestion;
  }

  private AiSuggestion load(UUID suggestionId) {
    return repository
        .findById(suggestionId)
        .orElseThrow(() -> new ResourceNotFoundException("Suggestion", suggestionId));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
