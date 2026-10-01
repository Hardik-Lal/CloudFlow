package com.cloudflow.environment.repository;

import com.cloudflow.environment.domain.Environment;
import com.cloudflow.environment.domain.EnvironmentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

  List<Environment> findAllByProjectId(UUID projectId);

  boolean existsByProjectIdAndType(UUID projectId, EnvironmentType type);
}
