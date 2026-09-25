package com.goodthingjar.pairing.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "couple_pair")
public class PairEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "time_zone", nullable = false, length = 64)
  private String timeZone;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private long version;

  protected PairEntity() {}

  public PairEntity(String zone, Instant now) {
    timeZone = zone;
    createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
