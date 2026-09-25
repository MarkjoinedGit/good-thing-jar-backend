package com.goodthingjar.jar.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UnlockProposalPolicy {
  public void requireFuture(Instant proposed, Instant now) {
    if (proposed == null || !proposed.isAfter(now))
      throw new IllegalStateException("Unlock instant must be in the future");
  }

  public void requireApprover(UUID proposer, UUID actor) {
    if (proposer.equals(actor)) throw new IllegalStateException("The other partner must approve");
  }

  public boolean derivedExpired(Instant now, Instant jarUnlock) {
    return !now.isBefore(jarUnlock);
  }
}
