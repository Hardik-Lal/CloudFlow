package com.cloudflow.assistant.service;

import com.cloudflow.assistant.client.AiModels.IndexRequest;
import com.cloudflow.assistant.client.AiModels.IndexResult;
import com.cloudflow.assistant.client.AiModels.KnowledgeDocument;
import com.cloudflow.assistant.client.AiModels.KnowledgeStats;
import com.cloudflow.assistant.client.AiServiceClient;
import com.cloudflow.assistant.dto.KnowledgeStatusResponse;
import com.cloudflow.assistant.dto.ReindexResponse;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.service.DeploymentService;
import com.cloudflow.deployment.service.DeploymentStatusChangedEvent;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.project.dto.ProjectSnapshot;
import com.cloudflow.project.service.ProjectAccessService;
import com.cloudflow.project.service.ProjectDeletedEvent;
import com.cloudflow.project.service.ProjectLookupService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the AI knowledge base in sync: a full re-index on request, plus incremental indexing of
 * every finished deployment so troubleshooting sees the latest history.
 */
@Service
public class KnowledgeService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
  private static final Set<DeploymentStatus> FINISHED =
      Set.of(DeploymentStatus.SUCCEEDED, DeploymentStatus.FAILED);

  private final AiServiceClient ai;
  private final KnowledgeCollector collector;
  private final ProjectAccessService projectAccess;
  private final ProjectLookupService projects;
  private final DeploymentService deployments;
  private final TaskExecutor executor;

  public KnowledgeService(
      AiServiceClient ai,
      KnowledgeCollector collector,
      ProjectAccessService projectAccess,
      ProjectLookupService projects,
      DeploymentService deployments,
      @Qualifier("aiTaskExecutor") TaskExecutor executor) {
    this.ai = ai;
    this.collector = collector;
    this.projectAccess = projectAccess;
    this.projects = projects;
    this.deployments = deployments;
    this.executor = executor;
  }

  public ReindexResponse reindex(UUID projectId, UUID userId) {
    projectAccess.requirePermission(projectId, userId, Permission.AI_USE);
    ProjectSnapshot project = projects.snapshot(projectId);
    List<KnowledgeDocument> documents = collector.collect(project, userId);
    IndexResult result = ai.index(new IndexRequest(projectId, documents, true));
    return new ReindexResponse(
        documents.size(), result.indexed(), result.unchanged(), result.removed());
  }

  public KnowledgeStatusResponse status(UUID projectId, UUID userId) {
    projectAccess.requirePermission(projectId, userId, Permission.PROJECT_READ);
    if (!ai.enabled()) {
      return new KnowledgeStatusResponse(false, 0, 0, null);
    }
    KnowledgeStats stats = ai.stats(projectId);
    return new KnowledgeStatusResponse(
        true, stats.documents(), stats.chunks(), stats.lastIndexedAt());
  }

  /** Indexes each finished deployment in the background; failures only affect AI features. */
  @TransactionalEventListener(fallbackExecution = true)
  public void onDeploymentStatus(DeploymentStatusChangedEvent event) {
    if (!ai.enabled() || !FINISHED.contains(event.status())) {
      return;
    }
    executor.execute(
        () -> {
          try {
            ai.index(
                new IndexRequest(
                    event.projectId(),
                    List.of(
                        collector.deploymentDocument(
                            deployments.getInternal(event.deploymentId()))),
                    false));
          } catch (RuntimeException e) {
            log.warn("Could not index deployment {}: {}", event.deploymentId(), e.getMessage());
          }
        });
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void onProjectDeleted(ProjectDeletedEvent event) {
    if (!ai.enabled()) {
      return;
    }
    executor.execute(
        () -> {
          try {
            ai.deleteProject(event.projectId());
          } catch (RuntimeException e) {
            log.warn(
                "Could not delete AI knowledge of project {}: {}",
                event.projectId(),
                e.getMessage());
          }
        });
  }
}
