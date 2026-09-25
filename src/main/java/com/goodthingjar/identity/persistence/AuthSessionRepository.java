package com.goodthingjar.identity.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {
  Optional<AuthSessionEntity> findByAccessTokenHash(String accessTokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from AuthSessionEntity s where s.refreshTokenHash = :hash or s.previousRefreshTokenHash = :hash")
  Optional<AuthSessionEntity> findByRefreshTokenForUpdate(@Param("hash") String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from AuthSessionEntity s where s.accessTokenHash = :hash")
  Optional<AuthSessionEntity> findByAccessTokenForUpdate(@Param("hash") String hash);
}
