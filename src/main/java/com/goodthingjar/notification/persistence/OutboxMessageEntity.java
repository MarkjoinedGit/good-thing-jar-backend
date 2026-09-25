package com.goodthingjar.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_message")
public class OutboxMessageEntity {
  public enum Status {
    PENDING,
    CLAIMED,
    DELIVERED,
    FAILED,
    SKIPPED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "message_type", nullable = false, length = 64)
  private String messageType;

  @Column(name = "aggregate_id")
  private UUID aggregateId;

  @Column(nullable = false, length = 320)
  private String recipient;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "encrypted_secret", columnDefinition = "text")
  private String encryptedSecret;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private long version;

  protected OutboxMessageEntity() {}

  public OutboxMessageEntity(
      String type,
      UUID aggregateId,
      String recipient,
      String payload,
      String encryptedSecret,
      Instant now) {
    this.messageType = type;
    this.aggregateId = aggregateId;
    this.recipient = recipient;
    this.payload = payload;
    this.encryptedSecret = encryptedSecret;
    this.status = Status.PENDING;
    this.availableAt = now;
    this.createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getMessageType() {
    return messageType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getRecipient() {
    return recipient;
  }

  public String getPayload() {
    return payload;
  }

  public String getEncryptedSecret() {
    return encryptedSecret;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getAvailableAt() {
    return availableAt;
  }

  public Instant getClaimedAt() {
    return claimedAt;
  }

  public void claim(Instant now) {
    status = Status.CLAIMED;
    claimedAt = now;
    processedAt = null;
    attempts++;
  }

  public void delivered(Instant now) {
    status = Status.DELIVERED;
    processedAt = now;
    encryptedSecret = null;
    lastErrorCode = null;
  }

  public void failed(Instant now, String code) {
    status = Status.FAILED;
    processedAt = now;
    encryptedSecret = null;
    lastErrorCode = code;
  }

  public void skipped(Instant now) {
    status = Status.SKIPPED;
    processedAt = now;
    encryptedSecret = null;
  }
}
