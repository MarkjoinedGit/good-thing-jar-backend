package com.goodthingjar.pairing.domain;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class InvitationPolicy {
  public static final Duration LIFETIME = Duration.ofDays(7);

  public Instant expiresAt(Instant createdAt) {
    return createdAt.plus(LIFETIME);
  }

  public boolean isExpired(Instant now, Instant expiresAt) {
    return !now.isBefore(expiresAt);
  }

  public boolean canAccept(InvitationStatus status) {
    return status == InvitationStatus.PENDING;
  }

  public void requireTransition(InvitationStatus from, InvitationStatus to) {
    if (from.terminal() || to.active() && !from.active())
      throw new IllegalStateException("Terminal invitations cannot reactivate");
    if (to == InvitationStatus.ACCEPTED && from != InvitationStatus.PENDING)
      throw new IllegalStateException("Only delivered invitations may be accepted");
  }
}
