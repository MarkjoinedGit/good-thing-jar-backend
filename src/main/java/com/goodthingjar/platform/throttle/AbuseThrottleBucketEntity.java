package com.goodthingjar.platform.throttle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "abuse_throttle_bucket")
@IdClass(AbuseThrottleBucketEntity.Key.class)
public class AbuseThrottleBucketEntity {
  @Id
  @Column(length = 48)
  private String operation;

  @Id
  @Column(name = "scope_hash", length = 64)
  private String scopeHash;

  @Id
  @Column(name = "window_started_at")
  private Instant windowStartedAt;

  @Column(name = "request_count", nullable = false)
  private int requestCount;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected AbuseThrottleBucketEntity() {}

  public record Key(String operation, String scopeHash, Instant windowStartedAt)
      implements Serializable {}
}
