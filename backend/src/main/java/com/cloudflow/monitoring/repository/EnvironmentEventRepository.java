package com.cloudflow.monitoring.repository;

import com.cloudflow.monitoring.domain.EnvironmentEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EnvironmentEventRepository extends JpaRepository<EnvironmentEvent, Long> {

  List<EnvironmentEvent> findAllByEnvironmentIdOrderByIdDesc(UUID environmentId, Limit limit);

  @Modifying
  @Query("delete from EnvironmentEvent e where e.createdAt < :before")
  int deleteOlderThan(Instant before);
}
