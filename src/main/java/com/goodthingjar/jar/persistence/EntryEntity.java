package com.goodthingjar.jar.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jar_entry")
public class EntryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "jar_id", nullable = false)
  private UUID jarId;

  @Column(name = "author_account_id", nullable = false)
  private UUID authorAccountId;

  @Column(nullable = false, length = 5000)
  private String text;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EntryEntity() {}

  public EntryEntity(UUID jarId, UUID author, String text, Instant now) {
    this.jarId = jarId;
    authorAccountId = author;
    this.text = text;
    createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getJarId() {
    return jarId;
  }

  public UUID getAuthorAccountId() {
    return authorAccountId;
  }

  public String getText() {
    return text;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
