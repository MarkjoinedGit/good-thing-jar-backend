package com.goodthingjar.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_session")
public class AuthSessionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "access_token_hash", nullable = false, unique = true, length = 64)
  private String accessTokenHash;

  @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
  private String refreshTokenHash;

  @Column(name = "previous_refresh_token_hash", length = 64)
  private String previousRefreshTokenHash;

  @Column(name = "access_expires_at", nullable = false)
  private Instant accessExpiresAt;

  @Column(name = "refresh_expires_at", nullable = false)
  private Instant refreshExpiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

  @Version private long version;

  protected AuthSessionEntity() {}

  public AuthSessionEntity(UUID accountId, String accessHash, String refreshHash, Instant now) {
    this.accountId = accountId;
    this.accessTokenHash = accessHash;
    this.refreshTokenHash = refreshHash;
    this.createdAt = now;
    this.accessExpiresAt = now.plusSeconds(15 * 60);
    this.refreshExpiresAt = now.plusSeconds(30L * 24 * 60 * 60);
  }

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public Instant getAccessExpiresAt() {
    return accessExpiresAt;
  }

  public Instant getRefreshExpiresAt() {
    return refreshExpiresAt;
  }

  public boolean accessUsableAt(Instant now) {
    return revokedAt == null && now.isBefore(accessExpiresAt);
  }

  public boolean refreshUsableAt(Instant now) {
    return revokedAt == null && now.isBefore(refreshExpiresAt);
  }

  public boolean isCurrentRefreshHash(String hash) {
    return refreshTokenHash.equals(hash);
  }

  public boolean isPreviousRefreshHash(String hash) {
    return previousRefreshTokenHash != null && previousRefreshTokenHash.equals(hash);
  }

  public void rotate(String accessHash, String refreshHash, Instant now) {
    previousRefreshTokenHash = this.refreshTokenHash;
    this.accessTokenHash = accessHash;
    this.refreshTokenHash = refreshHash;
    accessExpiresAt = now.plusSeconds(15 * 60);
    rotatedAt = now;
  }

  public void revoke(Instant now) {
    if (revokedAt == null) revokedAt = now;
  }
}
