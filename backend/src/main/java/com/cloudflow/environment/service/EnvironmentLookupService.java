package com.cloudflow.environment.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.environment.domain.Environment;
import com.cloudflow.environment.dto.EnvironmentSnapshot;
import com.cloudflow.environment.repository.EnvironmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal, unauthenticated environment lookups. Callers must already have authorized the user (see
 * {@link EnvironmentAccessService}).
 */
@Service
public class EnvironmentLookupService {

  private final EnvironmentRepository environmentRepository;

  public EnvironmentLookupService(EnvironmentRepository environmentRepository) {
    this.environmentRepository = environmentRepository;
  }

  @Transactional(readOnly = true)
  public EnvironmentSnapshot snapshot(UUID environmentId) {
    Environment environment =
        environmentRepository
            .findById(environmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Environment", environmentId));
    return new EnvironmentSnapshot(
        environment.getId(),
        environment.getProjectId(),
        environment.getType(),
        environment.getBranch());
  }
}
