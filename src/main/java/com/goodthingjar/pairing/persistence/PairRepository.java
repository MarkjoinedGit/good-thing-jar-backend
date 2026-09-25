package com.goodthingjar.pairing.persistence;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PairRepository extends JpaRepository<PairEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PairEntity p where p.id=:id")
  Optional<PairEntity> findByIdForUpdate(@Param("id") UUID id);
}
