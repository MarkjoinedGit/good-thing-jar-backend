package com.goodthingjar.pairing.application;

import com.goodthingjar.identity.application.IdentityAccess;
import com.goodthingjar.identity.application.RegistrationService;
import com.goodthingjar.notification.persistence.*;
import com.goodthingjar.pairing.domain.*;
import com.goodthingjar.pairing.persistence.*;
import com.goodthingjar.platform.error.*;
import com.goodthingjar.platform.throttle.AbuseThrottleService;
import com.goodthingjar.platform.time.TimeZoneRules;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {
  private final InvitationRepository invitations;
  private final PairAccessService pairs;
  private final IdentityAccess identity;
  private final OutboxMessageRepository outbox;
  private final AbuseThrottleService throttle;
  private final TimeZoneRules zones;
  private final InvitationPolicy policy;
  private final Clock clock;

  public InvitationService(
      InvitationRepository i,
      PairAccessService p,
      IdentityAccess a,
      OutboxMessageRepository o,
      AbuseThrottleService t,
      TimeZoneRules z,
      InvitationPolicy policy,
      Clock c) {
    invitations = i;
    pairs = p;
    identity = a;
    outbox = o;
    throttle = t;
    zones = z;
    this.policy = policy;
    clock = c;
  }

  @Transactional
  public InvitationEntity create(UUID inviter, String email, String zone) {
    String target = RegistrationService.normalizeEmail(email);
    throttle.check("invitation-create", inviter.toString());
    zones.requireIanaZone(zone);
    IdentityAccess.AccountView actor = identity.require(inviter);
    if (!actor.verified() || pairs.paired(inviter) || actor.normalizedEmail().equals(target))
      throw new ApiException(ProblemCode.CONFLICT, "Invitation cannot be created");
    Instant now = clock.instant();
    for (var existing : invitations.findActiveMatchingForUpdate(inviter, target)) {
      if (existing.expiredAt(now)) existing.expire(now);
      else throw new ApiException(ProblemCode.CONFLICT, "An active invitation already exists");
    }
    try {
      InvitationEntity invitation =
          invitations.saveAndFlush(
              new InvitationEntity(inviter, target, zone, now, policy.expiresAt(now)));
      queue(invitation);
      return invitation;
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ProblemCode.CONFLICT, "An active invitation already exists");
    }
  }

  @Transactional
  public List<InvitationEntity> list(UUID account) {
    var actor = identity.require(account);
    var visible = invitations.visibleTo(account, actor.normalizedEmail());
    var now = clock.instant();
    visible.stream().filter(i -> i.expiredAt(now)).forEach(i -> i.expire(now));
    return visible;
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void cancel(UUID account, UUID id) {
    InvitationEntity invitation = owned(id, account);
    Instant now = clock.instant();
    if (invitation.expiredAt(now)) {
      invitation.expire(now);
      throw new ApiException(ProblemCode.CONFLICT, "Invitation has expired");
    }
    try {
      invitation.cancel(now);
    } catch (IllegalStateException e) {
      throw new ApiException(ProblemCode.CONFLICT, "Invitation is no longer active");
    }
  }

  @Transactional(noRollbackFor = ApiException.class)
  public InvitationEntity retry(UUID account, UUID id) {
    throttle.check("invitation-retry", account.toString());
    InvitationEntity invitation = owned(id, account);
    Instant now = clock.instant();
    if (invitation.expiredAt(now)) {
      invitation.expire(now);
      throw new ApiException(ProblemCode.CONFLICT, "Invitation has expired");
    }
    if (invitation.getStatus() != InvitationStatus.DELIVERY_FAILED)
      throw new ApiException(ProblemCode.CONFLICT, "Invitation delivery is not retryable");
    invitation.deliveryQueued();
    queue(invitation);
    return invitation;
  }

  private InvitationEntity owned(UUID id, UUID account) {
    return invitations
        .findByIdForUpdate(id)
        .filter(i -> i.getInviterAccountId().equals(account))
        .orElseThrow(ProtectedResourceErrors::notFound);
  }

  private void queue(InvitationEntity invitation) {
    outbox.save(
        new OutboxMessageEntity(
            "INVITATION",
            invitation.getId(),
            invitation.getTargetEmailNormalized(),
            "You have a Good Thing Jar invitation: " + invitation.getId(),
            null,
            clock.instant()));
  }
}
