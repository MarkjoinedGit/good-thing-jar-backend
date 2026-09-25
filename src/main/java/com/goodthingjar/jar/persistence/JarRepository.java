package com.goodthingjar.jar.persistence;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface JarRepository extends JpaRepository<JarEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select j from JarEntity j where j.id=:id")
  Optional<JarEntity> findByIdForShare(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from JarEntity j where j.id=:id")
  Optional<JarEntity> findByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from JarEntity j where j.pairId=:pairId and j.current=true")
  Optional<JarEntity> findCurrentForUpdate(@Param("pairId") UUID pairId);

  Optional<JarEntity> findByIdAndPairId(UUID id, UUID pairId);

  List<JarEntity> findByPairIdOrderBySequenceNumberDesc(UUID pairId);
}
