package com.goodthingjar.pairing.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pair_member")
@IdClass(PairMemberEntity.Key.class)
public class PairMemberEntity {
  @Id
  @Column(name = "pair_id")
  private UUID pairId;

  @Id
  @Column(name = "account_id")
  private UUID accountId;

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  protected PairMemberEntity() {}

  public PairMemberEntity(UUID pairId, UUID accountId, Instant joinedAt) {
    this.pairId = pairId;
    this.accountId = accountId;
    this.joinedAt = joinedAt;
  }

  public UUID getPairId() {
    return pairId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public record Key(UUID pairId, UUID accountId) implements Serializable {}
}
