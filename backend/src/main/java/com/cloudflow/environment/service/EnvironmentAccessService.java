package com.cloudflow.environment.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.environment.domain.Environment;
import com.cloudflow.environment.domain.EnvironmentType;
import com.cloudflow.environment.dto.EnvironmentRef;
import com.cloudflow.environment.repository.EnvironmentRepository;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.project.dto.ProjectRef;
import com.cloudflow.project.service.ProjectAccessService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorizes environment-scoped actions. Production environments can require a stronger permission
 * than the others (e.g. {@code ENV_WRITE_PRODUCTION} instead of {@code ENV_WRITE}).
 */
@Service
public class EnvironmentAccessService {

  private final EnvironmentRepository environmentRepository;
  private final ProjectAccessService projectAccessService;

  public EnvironmentAccessService(
      EnvironmentRepository environmentRepository, ProjectAccessService projectAccessService) {
    this.environmentRepository = environmentRepository;
    this.projectAccessService = projectAccessService;
  }

  @Transactional(readOnly = true)
  public EnvironmentRef requireRead(UUID environmentId, UUID userId) {
    return require(environmentId, userId, Permission.ENV_READ, Permission.ENV_READ);
  }

  @Transactional(readOnly = true)
  public EnvironmentRef requireWrite(UUID environmentId, UUID userId) {
    return require(environmentId, userId, Permission.ENV_WRITE, Permission.ENV_WRITE_PRODUCTION);
  }

  /**
   * @param permission required for Development and Staging
   * @param productionPermission required for Production
   */
  @Transactional(readOnly = true)
  public EnvironmentRef require(
      UUID environmentId, UUID userId, Permission permission, Permission productionPermission) {
    Environment environment =
        environmentRepository
            .findById(environmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Environment", environmentId));
    ProjectRef project;
    try {
      project =
          projectAccessService.requirePermission(
              environment.getProjectId(),
              userId,
              permissionFor(environment.getType(), permission, productionPermission));
    } catch (ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Environment", environmentId);
    }
    return new EnvironmentRef(
        environment.getId(),
        environment.getProjectId(),
        project.organizationId(),
        environment.getType(),
        environment.getBranch(),
        project.role());
  }

  static Permission permissionFor(
      EnvironmentType type, Permission permission, Permission productionPermission) {
    return type.isProduction() ? productionPermission : permission;
  }
}
