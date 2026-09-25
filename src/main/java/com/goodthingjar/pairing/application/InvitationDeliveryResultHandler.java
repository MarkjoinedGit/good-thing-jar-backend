package com.goodthingjar.pairing.application;

import com.goodthingjar.notification.application.InvitationDeliveryPort;
import com.goodthingjar.pairing.persistence.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationDeliveryResultHandler implements InvitationDeliveryPort {
  private final InvitationRepository invitations;
  private final Clock clock;

  public InvitationDeliveryResultHandler(InvitationRepository i, Clock c) {
    invitations = i;
    clock = c;
  }

  @Transactional
  public boolean mayDeliver(UUID id) {
    var invitation = invitations.findByIdForUpdate(id).orElse(null);
    if (invitation == null) return false;
    var now = clock.instant();
    if (invitation.expiredAt(now)) {
      invitation.expire(now);
      return false;
    }
    return invitation.getStatus()
        == com.goodthingjar.pairing.domain.InvitationStatus.PENDING_DELIVERY;
  }

  @Transactional
  public void recordResult(UUID id, boolean delivered) {
    invitations
        .findByIdForUpdate(id)
        .ifPresent(
            i -> {
              var now = clock.instant();
              if (i.expiredAt(now)) i.expire(now);
              else if (delivered) i.delivered(now);
              else i.deliveryFailed(now);
            });
  }
}
