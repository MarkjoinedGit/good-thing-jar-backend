package com.goodthingjar.pairing.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PairMemberRepository
    extends JpaRepository<PairMemberEntity, PairMemberEntity.Key> {
  Optional<PairMemberEntity> findByAccountId(UUID accountId);

  List<PairMemberEntity> findByPairIdOrderByAccountId(UUID pairId);

  boolean existsByAccountId(UUID accountId);
}
