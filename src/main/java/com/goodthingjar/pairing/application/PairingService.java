package com.goodthingjar.pairing.application;

import com.goodthingjar.identity.application.IdentityAccess;
import com.goodthingjar.pairing.api.PairCreated;
import com.goodthingjar.pairing.domain.InvitationStatus;
import com.goodthingjar.pairing.persistence.*;
import com.goodthingjar.platform.error.*;
import java.time.Clock;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PairingService {
  private final InvitationRepository invitations;
  private final PairRepository pairs;
  private final PairMemberRepository members;
  private final IdentityAccess identity;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public PairingService(
      InvitationRepository i,
      PairRepository p,
      PairMemberRepository m,
      IdentityAccess a,
      ApplicationEventPublisher e,
      Clock c) {
    invitations = i;
    pairs = p;
    members = m;
    identity = a;
    events = e;
    clock = c;
  }

  @Transactional(noRollbackFor = ApiException.class)
  public PairEntity accept(UUID accountId, UUID invitationId) {
    var now = clock.instant();
    var preview = invitations.findById(invitationId).orElseThrow(ProtectedResourceErrors::notFound);
    var target = identity.require(accountId);
    if (!target.verified() || !target.normalizedEmail().equals(preview.getTargetEmailNormalized()))
      throw ProtectedResourceErrors.notFound();
    identity.lockOrdered(accountId, preview.getInviterAccountId());
    var invitation =
        invitations.findByIdForUpdate(invitationId).orElseThrow(ProtectedResourceErrors::notFound);
    target = identity.require(accountId);
    if (!target.verified()
        || !target.normalizedEmail().equals(invitation.getTargetEmailNormalized()))
      throw ProtectedResourceErrors.notFound();
    if (invitation.expiredAt(now)) {
      invitation.expire(now);
      throw new ApiException(ProblemCode.CONFLICT, "Invitation has expired");
    }
    if (invitation.getStatus() != InvitationStatus.PENDING)
      throw new ApiException(ProblemCode.CONFLICT, "Invitation is not ready for acceptance");
    if (members.existsByAccountId(accountId)
        || members.existsByAccountId(invitation.getInviterAccountId()))
      throw new ApiException(ProblemCode.CONFLICT, "An account is already paired");
    try {
      PairEntity pair = pairs.saveAndFlush(new PairEntity(invitation.getTimeZone(), now));
      members.saveAndFlush(new PairMemberEntity(pair.getId(), accountId, now));
      members.saveAndFlush(
          new PairMemberEntity(pair.getId(), invitation.getInviterAccountId(), now));
      invitation.accept(now);
      for (UUID participant : List.of(accountId, invitation.getInviterAccountId())) {
        var view = identity.require(participant);
        for (var other : invitations.activeInvolvingForUpdate(participant, view.normalizedEmail()))
          if (!other.getId().equals(invitationId)) other.invalidate(now);
      }
      events.publishEvent(new PairCreated(pair.getId(), pair.getTimeZone(), now));
      return pair;
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ProblemCode.CONFLICT, "Pairing conflict");
    }
  }
}
