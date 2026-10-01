package com.cloudflow.project.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.common.exception.ConflictException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.common.util.Slugs;
import com.cloudflow.github.client.GithubModels.Repository;
import com.cloudflow.github.dto.BranchResponse;
import com.cloudflow.github.dto.CommitResponse;
import com.cloudflow.github.dto.PullRequestResponse;
import com.cloudflow.github.service.GithubService;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.organization.domain.Role;
import com.cloudflow.organization.service.OrganizationAccessService;
import com.cloudflow.project.domain.AppType;
import com.cloudflow.project.domain.AppTypeDetector;
import com.cloudflow.project.domain.BranchMetadata;
import com.cloudflow.project.domain.Project;
import com.cloudflow.project.domain.RepositoryMetadata;
import com.cloudflow.project.domain.SourceRepository;
import com.cloudflow.project.dto.CreateProjectRequest;
import com.cloudflow.project.dto.ProjectResponse;
import com.cloudflow.project.dto.ProjectSummaryResponse;
import com.cloudflow.project.dto.UpdateProjectRequest;
import com.cloudflow.project.repository.ProjectRepository;
import com.cloudflow.project.service.ProjectAccessService.AuthorizedProject;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Project lifecycle. Calls to GitHub happen outside database transactions so that slow GitHub
 * responses never hold a database connection.
 */
@Service
public class ProjectService {

  private final AuditLogger audit;

  private final ProjectRepository projectRepository;
  private final ProjectAccessService projectAccessService;
  private final OrganizationAccessService organizationAccessService;
  private final GithubService githubService;
  private final TransactionTemplate transactionTemplate;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ProjectService(
      ProjectRepository projectRepository,
      ProjectAccessService projectAccessService,
      OrganizationAccessService organizationAccessService,
      GithubService githubService,
      TransactionTemplate transactionTemplate,
      ApplicationEventPublisher events,
      Clock clock,
      AuditLogger audit) {
    this.audit = audit;
    this.projectRepository = projectRepository;
    this.projectAccessService = projectAccessService;
    this.organizationAccessService = organizationAccessService;
    this.githubService = githubService;
    this.transactionTemplate = transactionTemplate;
    this.events = events;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ProjectSummaryResponse> list(UUID organizationId, UUID userId) {
    organizationAccessService.requirePermission(organizationId, userId, Permission.PROJECT_READ);
    return projectRepository.findAllByOrganizationIdOrderByNameAsc(organizationId).stream()
        .map(ProjectSummaryResponse::from)
        .toList();
  }

  /** Creates a project from a GitHub repository the caller can access. */
  public ProjectResponse create(UUID organizationId, UUID userId, CreateProjectRequest request) {
    Role role =
        organizationAccessService.requirePermission(
            organizationId, userId, Permission.PROJECT_WRITE);
    String[] ownerAndName = request.repositoryFullName().split("/", 2);
    GithubSnapshot snapshot = fetchSnapshot(userId, ownerAndName[0], ownerAndName[1]);

    String name = isBlank(request.name()) ? snapshot.repository().name() : request.name().strip();
    String slug = Slugs.requestedOrDerived(request.slug(), name);
    String description = isBlank(request.description()) ? null : request.description().strip();

    Project saved =
        transactionTemplate.execute(
            status -> {
              if (projectRepository.existsByOrganizationIdAndGithubRepoId(
                  organizationId, snapshot.repository().githubRepoId())) {
                throw new ConflictException(
                    snapshot.repository().fullName()
                        + " is already linked to a project in this organization");
              }
              if (projectRepository.existsByOrganizationIdAndSlug(organizationId, slug)) {
                throw new ConflictException("A project with slug '" + slug + "' already exists");
              }
              Project project =
                  new Project(
                      organizationId,
                      name,
                      slug,
                      description,
                      userId,
                      snapshot.repository(),
                      snapshot.appType(),
                      clock.instant());
              project.getRepository().syncBranches(snapshot.branches());
              Project savedProject = projectRepository.save(project);
              audit.record(
                  organizationId,
                  userId,
                  AuditAction.PROJECT_CREATED,
                  "project",
                  savedProject.getId(),
                  Map.of("name", name, "repository", snapshot.repository().fullName()));
              return savedProject;
            });
    return ProjectResponse.from(saved, role);
  }

  @Transactional(readOnly = true)
  public ProjectResponse get(UUID projectId, UUID userId) {
    AuthorizedProject authorized =
        projectAccessService.loadAuthorized(projectId, userId, Permission.PROJECT_READ);
    return ProjectResponse.from(authorized.project(), authorized.role());
  }

  @Transactional
  public ProjectResponse update(UUID projectId, UUID userId, UpdateProjectRequest request) {
    AuthorizedProject authorized =
        projectAccessService.loadAuthorized(projectId, userId, Permission.PROJECT_WRITE);
    Project project = authorized.project();
    project.updateDetails(
        request.name().strip(),
        isBlank(request.description()) ? null : request.description().strip());
    audit.record(
        project.getOrganizationId(),
        userId,
        AuditAction.PROJECT_UPDATED,
        "project",
        projectId,
        Map.of("name", project.getName()));
    return ProjectResponse.from(project, authorized.role());
  }

  @Transactional
  public void delete(UUID projectId, UUID userId) {
    AuthorizedProject authorized =
        projectAccessService.loadAuthorized(projectId, userId, Permission.PROJECT_DELETE);
    projectRepository.delete(authorized.project());
    audit.record(
        authorized.project().getOrganizationId(),
        userId,
        AuditAction.PROJECT_DELETED,
        "project",
        projectId,
        Map.of("name", authorized.project().getName()));
    events.publishEvent(new ProjectDeletedEvent(projectId));
  }

  /** Refreshes repository metadata, branches, and the detected application type from GitHub. */
  public ProjectResponse sync(UUID projectId, UUID userId) {
    AuthorizedProject authorized =
        transactionTemplate.execute(
            status ->
                projectAccessService.loadAuthorized(projectId, userId, Permission.PROJECT_WRITE));
    SourceRepository current = authorized.project().getRepository();
    GithubSnapshot snapshot = fetchSnapshot(userId, current.getOwner(), current.getName());

    Project updated =
        transactionTemplate.execute(
            status -> {
              Project project =
                  projectRepository
                      .findById(projectId)
                      .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
              project.getRepository().apply(snapshot.repository(), clock.instant());
              project.getRepository().syncBranches(snapshot.branches());
              project.changeAppType(snapshot.appType());
              return project;
            });
    return ProjectResponse.from(updated, authorized.role());
  }

  @Transactional(readOnly = true)
  public List<BranchResponse> branches(UUID projectId, UUID userId) {
    Project project =
        projectAccessService.loadAuthorized(projectId, userId, Permission.PROJECT_READ).project();
    return project.getRepository().getBranches().stream()
        .map(
            branch ->
                new BranchResponse(
                    branch.getName(), branch.getHeadCommitSha(), branch.isProtected()))
        .toList();
  }

  public List<CommitResponse> commits(UUID projectId, UUID userId, String branch) {
    SourceRepository repository = authorizedRepository(projectId, userId);
    String ref = isBlank(branch) ? repository.getDefaultBranch() : branch;
    return githubService.listCommits(userId, repository.getOwner(), repository.getName(), ref);
  }

  public List<PullRequestResponse> pullRequests(UUID projectId, UUID userId, String state) {
    SourceRepository repository = authorizedRepository(projectId, userId);
    return githubService.listPullRequests(
        userId, repository.getOwner(), repository.getName(), state);
  }

  private SourceRepository authorizedRepository(UUID projectId, UUID userId) {
    return transactionTemplate.execute(
        status ->
            projectAccessService
                .loadAuthorized(projectId, userId, Permission.PROJECT_READ)
                .project()
                .getRepository());
  }

  private GithubSnapshot fetchSnapshot(UUID userId, String owner, String name) {
    Repository repository = githubService.getRepository(userId, owner, name);
    RepositoryMetadata metadata =
        new RepositoryMetadata(
            repository.id(),
            repository.owner().login(),
            repository.name(),
            repository.fullName(),
            repository.htmlUrl(),
            repository.cloneUrl(),
            repository.defaultBranch(),
            repository.isPrivate(),
            repository.language());
    List<BranchMetadata> branches =
        githubService.listBranches(userId, metadata.owner(), metadata.name()).stream()
            .map(
                branch ->
                    new BranchMetadata(branch.name(), branch.commit().sha(), branch.isProtected()))
            .toList();
    return new GithubSnapshot(metadata, branches, detectAppType(userId, metadata));
  }

  private AppType detectAppType(UUID userId, RepositoryMetadata repository) {
    try {
      return AppTypeDetector.detect(
          githubService.listRootFileNames(
              userId, repository.owner(), repository.name(), repository.defaultBranch()));
    } catch (ResourceNotFoundException e) {
      // GitHub answers 404 for the contents of an empty repository.
      return AppType.UNKNOWN;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record GithubSnapshot(
      RepositoryMetadata repository, List<BranchMetadata> branches, AppType appType) {}
}
