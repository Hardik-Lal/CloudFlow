package com.cloudflow.audit.repository;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  @Query(
      "select a from AuditLog a where a.organizationId = :organizationId"
          + " and (:action is null or a.action = :action)"
          + " and (:actorId is null or a.actorId = :actorId)"
          + " and a.createdAt >= :from and a.createdAt < :to")
  Page<AuditLog> search(
      UUID organizationId,
      AuditAction action,
      UUID actorId,
      Instant from,
      Instant to,
      Pageable pageable);
}
