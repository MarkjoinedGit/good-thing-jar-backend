package com.goodthingjar.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification")
public class EmailVerificationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected EmailVerificationEntity() {}

  public EmailVerificationEntity(UUID accountId, String tokenHash, Instant issuedAt) {
    this.accountId = accountId;
    this.tokenHash = tokenHash;
    this.issuedAt = issuedAt;
    this.expiresAt = issuedAt.plusSeconds(24 * 60 * 60);
  }

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean usableAt(Instant now) {
    return consumedAt == null && supersededAt == null && now.isBefore(expiresAt);
  }

  public void consume(Instant at) {
    consumedAt = at;
  }

  public void supersede(Instant at) {
    supersededAt = at;
  }
}
