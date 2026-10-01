package com.cloudflow.cicd.repository;

import com.cloudflow.cicd.domain.DeployToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeployTokenRepository extends JpaRepository<DeployToken, UUID> {

  Optional<DeployToken> findByTokenHash(String tokenHash);

  List<DeployToken> findAllByEnvironmentIdOrderByCreatedAtDesc(UUID environmentId);
}
