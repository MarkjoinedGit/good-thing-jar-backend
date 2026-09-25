package com.goodthingjar.pairing.persistence;

import com.goodthingjar.pairing.domain.InvitationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitation")
public class InvitationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "inviter_account_id", nullable = false)
  private UUID inviterAccountId;

  @Column(name = "target_email_normalized", nullable = false, length = 320)
  private String targetEmailNormalized;

  @Column(name = "time_zone", nullable = false, length = 64)
  private String timeZone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InvitationStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "terminal_at")
  private Instant terminalAt;

  @Version private long version;

  protected InvitationEntity() {}

  public InvitationEntity(UUID inviter, String target, String zone, Instant now, Instant expires) {
    inviterAccountId = inviter;
    targetEmailNormalized = target;
    timeZone = zone;
    createdAt = now;
    expiresAt = expires;
    status = InvitationStatus.PENDING_DELIVERY;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInviterAccountId() {
    return inviterAccountId;
  }

  public String getTargetEmailNormalized() {
    return targetEmailNormalized;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public InvitationStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean expiredAt(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public void expire(Instant now) {
    if (status.active()) {
      status = InvitationStatus.EXPIRED;
      terminalAt = now;
    }
  }

  public void cancel(Instant now) {
    if (!status.active()) throw new IllegalStateException("Invitation is not active");
    status = InvitationStatus.CANCELLED;
    terminalAt = now;
  }

  public void deliveryQueued() {
    if (status != InvitationStatus.DELIVERY_FAILED)
      throw new IllegalStateException("Only failed delivery can be retried");
    status = InvitationStatus.PENDING_DELIVERY;
  }

  public void delivered(Instant now) {
    if (status == InvitationStatus.PENDING_DELIVERY && !expiredAt(now)) {
      status = InvitationStatus.PENDING;
      deliveredAt = now;
    }
  }

  public void deliveryFailed(Instant now) {
    if (status == InvitationStatus.PENDING_DELIVERY && !expiredAt(now))
      status = InvitationStatus.DELIVERY_FAILED;
  }

  public void accept(Instant now) {
    if (status != InvitationStatus.PENDING || expiredAt(now))
      throw new IllegalStateException("Invitation cannot be accepted");
    status = InvitationStatus.ACCEPTED;
    terminalAt = now;
  }

  public void invalidate(Instant now) {
    if (status.active()) {
      status = InvitationStatus.INVALIDATED;
      terminalAt = now;
    }
  }
}
