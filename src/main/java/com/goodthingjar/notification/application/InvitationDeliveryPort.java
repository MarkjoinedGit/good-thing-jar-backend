package com.goodthingjar.notification.application;

import java.util.UUID;

public interface InvitationDeliveryPort {
  boolean mayDeliver(UUID invitationId);

  void recordResult(UUID invitationId, boolean delivered);
}
