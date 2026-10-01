package com.cloudflow.auth.repository;

import com.cloudflow.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /** Locks the row so two concurrent refreshes with the same token cannot both succeed. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :at where t.userId = :userId and t.revokedAt is null")
  int revokeAllForUser(UUID userId, Instant at);

  @Modifying
  @Query("delete from RefreshToken t where t.expiresAt < :before")
  int deleteExpiredBefore(Instant before);
}
