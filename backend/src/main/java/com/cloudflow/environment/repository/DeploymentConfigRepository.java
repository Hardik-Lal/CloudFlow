package com.cloudflow.environment.repository;

import com.cloudflow.environment.domain.DeploymentConfig;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentConfigRepository extends JpaRepository<DeploymentConfig, UUID> {}
