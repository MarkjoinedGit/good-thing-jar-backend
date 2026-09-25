package com.goodthingjar.jar.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unlock_proposal")
public class UnlockProposalEntity {
  public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "jar_id", nullable = false)
  private UUID jarId;

  @Column(name = "proposed_by_account_id", nullable = false)
  private UUID proposedByAccountId;

  @Column(name = "proposed_unlock_at", nullable = false)
  private Instant proposedUnlockAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Version private long version;

  protected UnlockProposalEntity() {}

  public UnlockProposalEntity(UUID jar, UUID proposer, Instant proposed, Instant now) {
    jarId = jar;
    proposedByAccountId = proposer;
    proposedUnlockAt = proposed;
    createdAt = now;
    status = Status.PENDING;
  }

  public UUID getId() {
    return id;
  }

  public UUID getJarId() {
    return jarId;
  }

  public UUID getProposedByAccountId() {
    return proposedByAccountId;
  }

  public Instant getProposedUnlockAt() {
    return proposedUnlockAt;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void approve(Instant now) {
    requirePending();
    status = Status.APPROVED;
    resolvedAt = now;
  }

  public void reject(Instant now) {
    requirePending();
    status = Status.REJECTED;
    resolvedAt = now;
  }

  public void cancel(Instant now) {
    requirePending();
    status = Status.CANCELLED;
    resolvedAt = now;
  }

  public void expire(Instant now) {
    if (status == Status.PENDING) {
      status = Status.EXPIRED;
      resolvedAt = now;
    }
  }

  private void requirePending() {
    if (status != Status.PENDING) throw new IllegalStateException("Proposal is not pending");
  }
}
