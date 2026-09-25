package com.goodthingjar.jar.persistence;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UnlockProposalRepository extends JpaRepository<UnlockProposalEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from UnlockProposalEntity p where p.id=:id and p.jarId=:jar")
  Optional<UnlockProposalEntity> findForUpdate(@Param("jar") UUID jar, @Param("id") UUID id);

  Optional<UnlockProposalEntity> findByJarIdAndStatus(
      UUID jarId, UnlockProposalEntity.Status status);
}
