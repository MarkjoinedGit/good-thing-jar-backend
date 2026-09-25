package com.goodthingjar.identity.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, UUID> {
  Optional<EmailVerificationEntity> findByTokenHash(String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from EmailVerificationEntity v where v.tokenHash = :hash")
  Optional<EmailVerificationEntity> findByTokenHashForUpdate(@Param("hash") String hash);

  @Query(
      "select v from EmailVerificationEntity v where v.accountId = :accountId and v.consumedAt is null and v.supersededAt is null")
  List<EmailVerificationEntity> findUnsuperseded(@Param("accountId") UUID accountId);

  @Modifying
  @Query(
      "update EmailVerificationEntity v set v.supersededAt = :at where v.accountId = :accountId and v.consumedAt is null and v.supersededAt is null")
  int supersedeAll(@Param("accountId") UUID accountId, @Param("at") Instant at);
}
