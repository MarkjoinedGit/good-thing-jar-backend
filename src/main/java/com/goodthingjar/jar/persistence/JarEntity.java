package com.goodthingjar.jar.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shared_jar")
public class JarEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "pair_id", nullable = false)
  private UUID pairId;

  @Column(name = "sequence_number", nullable = false)
  private int sequenceNumber;

  @Column(nullable = false)
  private boolean current;

  @Column(name = "time_zone", nullable = false, length = 64)
  private String timeZone;

  @Column(name = "effective_unlock_at", nullable = false)
  private Instant effectiveUnlockAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private long version;

  protected JarEntity() {}

  public JarEntity(UUID pairId, int sequence, String zone, Instant unlockAt, Instant now) {
    this.pairId = pairId;
    sequenceNumber = sequence;
    timeZone = zone;
    effectiveUnlockAt = unlockAt;
    createdAt = now;
    current = true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPairId() {
    return pairId;
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public boolean isCurrent() {
    return current;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public Instant getEffectiveUnlockAt() {
    return effectiveUnlockAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void closeCycle() {
    current = false;
  }

  public void changeUnlock(Instant value) {
    effectiveUnlockAt = value;
  }
}
