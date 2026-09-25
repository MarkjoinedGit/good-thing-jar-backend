package com.goodthingjar.notification.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from OutboxMessageEntity o where (o.status = 'PENDING' and o.availableAt <= :now) or (o.status = 'CLAIMED' and o.claimedAt <= :staleBefore) order by o.createdAt")
  List<OutboxMessageEntity> claimable(
      @Param("now") Instant now, @Param("staleBefore") Instant staleBefore, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OutboxMessageEntity o where o.id = :id")
  Optional<OutboxMessageEntity> findByIdForUpdate(@Param("id") UUID id);

  long countByStatus(OutboxMessageEntity.Status status);
}
