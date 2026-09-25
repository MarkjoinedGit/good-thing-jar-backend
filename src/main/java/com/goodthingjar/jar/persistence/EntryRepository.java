package com.goodthingjar.jar.persistence;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<EntryEntity, UUID> {
  @Query("select e from EntryEntity e where e.jarId=:jarId order by e.createdAt,e.id")
  List<EntryEntity> firstPage(@Param("jarId") UUID jarId, Pageable pageable);

  @Query(
      "select e from EntryEntity e where e.jarId=:jarId and (e.createdAt>:created or (e.createdAt=:created and e.id>:id)) order by e.createdAt,e.id")
  List<EntryEntity> pageAfter(
      @Param("jarId") UUID jarId,
      @Param("created") Instant created,
      @Param("id") UUID id,
      Pageable pageable);
}
