package com.cloudflow.environment.repository;

import com.cloudflow.environment.domain.EnvironmentVariable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, UUID> {

  List<EnvironmentVariable> findAllByEnvironmentIdOrderByKeyAsc(UUID environmentId);

  Optional<EnvironmentVariable> findByEnvironmentIdAndKey(UUID environmentId, String key);

  interface VariableCounts {
    UUID getEnvironmentId();

    long getTotal();

    long getSecrets();
  }

  @Query(
      "select v.environmentId as environmentId, count(v) as total,"
          + " sum(case when v.secret = true then 1 else 0 end) as secrets"
          + " from EnvironmentVariable v where v.environmentId in :environmentIds"
          + " group by v.environmentId")
  List<VariableCounts> countByEnvironmentIds(List<UUID> environmentIds);
}
