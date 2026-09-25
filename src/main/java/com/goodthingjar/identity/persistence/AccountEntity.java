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
@Table(name = "account")
public class AccountEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "email_normalized", nullable = false, unique = true, length = 320)
  private String emailNormalized;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private long version;

  protected AccountEntity() {}

  public AccountEntity(String emailNormalized, String passwordHash, Instant createdAt) {
    this.emailNormalized = emailNormalized;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmailNormalized() {
    return emailNormalized;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public boolean isVerified() {
    return verifiedAt != null;
  }

  public void verify(Instant at) {
    if (verifiedAt == null) verifiedAt = at;
  }
}
