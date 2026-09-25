package com.goodthingjar.jar.application;

import com.goodthingjar.jar.domain.*;
import com.goodthingjar.jar.persistence.*;
import com.goodthingjar.pairing.application.PairAccessService;
import com.goodthingjar.platform.error.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnlockProposalService {
  private final UnlockProposalRepository proposals;
  private final JarRepository jars;
  private final PairAccessService pairs;
  private final JarLockPolicy locks;
  private final UnlockProposalPolicy policy;
  private final Clock clock;

  public UnlockProposalService(
      UnlockProposalRepository p,
      JarRepository j,
      PairAccessService pairs,
      JarLockPolicy l,
      UnlockProposalPolicy policy,
      Clock c) {
    proposals = p;
    jars = j;
    this.pairs = pairs;
    locks = l;
    this.policy = policy;
    clock = c;
  }

  @Transactional
  public UnlockProposalEntity propose(UUID account, UUID jarId, Instant proposed) {
    Instant now = clock.instant();
    JarEntity jar = memberJar(account, jarId);
    if (!locks.isLocked(now, jar.getEffectiveUnlockAt()))
      throw new ApiException(ProblemCode.CONFLICT, "Unlocked jars cannot be changed");
    try {
      policy.requireFuture(proposed, now);
    } catch (IllegalStateException e) {
      throw new ApiException(ProblemCode.VALIDATION, e.getMessage());
    }
    proposals
        .findByJarIdAndStatus(jarId, UnlockProposalEntity.Status.PENDING)
        .ifPresent(
            existing -> {
              if (policy.derivedExpired(now, jar.getEffectiveUnlockAt())) existing.expire(now);
              else throw new ApiException(ProblemCode.CONFLICT, "A proposal is already pending");
            });
    try {
      return proposals.saveAndFlush(new UnlockProposalEntity(jarId, account, proposed, now));
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ProblemCode.CONFLICT, "A proposal is already pending");
    }
  }

  @Transactional(noRollbackFor = ApiException.class)
  public JarEntity approve(UUID account, UUID jarId, UUID proposalId) {
    Instant now = clock.instant();
    UnlockProposalEntity proposal =
        proposals.findForUpdate(jarId, proposalId).orElseThrow(ProtectedResourceErrors::notFound);
    JarEntity jar = jars.findByIdForUpdate(jarId).orElseThrow(ProtectedResourceErrors::notFound);
    pairs.requireMember(jar.getPairId(), account);
    requirePendingLocked(proposal, jar, now);
    try {
      policy.requireApprover(proposal.getProposedByAccountId(), account);
      policy.requireFuture(proposal.getProposedUnlockAt(), now);
    } catch (IllegalStateException e) {
      throw new ApiException(ProblemCode.CONFLICT, e.getMessage());
    }
    jar.changeUnlock(proposal.getProposedUnlockAt());
    proposal.approve(now);
    return jar;
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void reject(UUID account, UUID jarId, UUID proposalId) {
    resolve(account, jarId, proposalId, false);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void cancel(UUID account, UUID jarId, UUID proposalId) {
    Instant now = clock.instant();
    var proposal =
        proposals.findForUpdate(jarId, proposalId).orElseThrow(ProtectedResourceErrors::notFound);
    var jar = jars.findByIdForUpdate(jarId).orElseThrow(ProtectedResourceErrors::notFound);
    pairs.requireMember(jar.getPairId(), account);
    requirePendingLocked(proposal, jar, now);
    if (!proposal.getProposedByAccountId().equals(account))
      throw new ApiException(ProblemCode.CONFLICT, "Only the proposer may cancel");
    proposal.cancel(now);
  }

  private void resolve(UUID account, UUID jarId, UUID proposalId, boolean ignored) {
    Instant now = clock.instant();
    var proposal =
        proposals.findForUpdate(jarId, proposalId).orElseThrow(ProtectedResourceErrors::notFound);
    var jar = jars.findByIdForUpdate(jarId).orElseThrow(ProtectedResourceErrors::notFound);
    pairs.requireMember(jar.getPairId(), account);
    requirePendingLocked(proposal, jar, now);
    try {
      policy.requireApprover(proposal.getProposedByAccountId(), account);
    } catch (IllegalStateException e) {
      throw new ApiException(ProblemCode.CONFLICT, e.getMessage());
    }
    proposal.reject(now);
  }

  private JarEntity memberJar(UUID account, UUID jarId) {
    JarEntity jar = jars.findByIdForUpdate(jarId).orElseThrow(ProtectedResourceErrors::notFound);
    pairs.requireMember(jar.getPairId(), account);
    return jar;
  }

  private void requirePendingLocked(UnlockProposalEntity p, JarEntity j, Instant now) {
    if (p.getStatus() != UnlockProposalEntity.Status.PENDING)
      throw new ApiException(ProblemCode.CONFLICT, "Proposal is no longer pending");
    if (!locks.isLocked(now, j.getEffectiveUnlockAt())) {
      p.expire(now);
      throw new ApiException(ProblemCode.CONFLICT, "Proposal expired when the jar unlocked");
    }
  }

  @Transactional(readOnly = true)
  public Optional<UnlockProposalEntity> pending(UUID account, JarEntity jar) {
    pairs.requireMember(jar.getPairId(), account);
    return proposals
        .findByJarIdAndStatus(jar.getId(), UnlockProposalEntity.Status.PENDING)
        .filter(p -> !policy.derivedExpired(clock.instant(), jar.getEffectiveUnlockAt()));
  }
}
