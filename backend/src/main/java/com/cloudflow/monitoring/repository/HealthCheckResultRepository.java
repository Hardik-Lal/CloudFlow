package com.cloudflow.monitoring.repository;

import com.cloudflow.monitoring.domain.HealthCheckResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HealthCheckResultRepository extends JpaRepository<HealthCheckResult, Long> {

  List<HealthCheckResult> findAllByEnvironmentIdAndCheckedAtAfterOrderByCheckedAtAsc(
      UUID environmentId, Instant after);

  @Modifying
  @Query("delete from HealthCheckResult r where r.checkedAt < :before")
  int deleteOlderThan(Instant before);
}
