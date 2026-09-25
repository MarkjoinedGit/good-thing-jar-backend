package com.goodthingjar.pairing.domain;

public enum InvitationStatus {
  PENDING_DELIVERY,
  PENDING,
  DELIVERY_FAILED,
  ACCEPTED,
  CANCELLED,
  EXPIRED,
  INVALIDATED;

  public boolean active() {
    return this == PENDING_DELIVERY || this == PENDING || this == DELIVERY_FAILED;
  }

  public boolean terminal() {
    return !active();
  }
}
