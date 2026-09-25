package com.goodthingjar.jar;

import static org.assertj.core.api.Assertions.*;

import com.goodthingjar.jar.domain.UnlockProposalPolicy;
import com.goodthingjar.jar.persistence.UnlockProposalEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnlockProposalPolicyTest {
  private final UnlockProposalPolicy policy = new UnlockProposalPolicy();

  @Test
  void proposerCannotApprove() {
    UUID proposer = UUID.randomUUID();
    assertThatThrownBy(() -> policy.requireApprover(proposer, proposer))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void proposedInstantMustRemainFuture() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    assertThatThrownBy(() -> policy.requireFuture(now, now))
        .isInstanceOf(IllegalStateException.class);
    policy.requireFuture(now.plusSeconds(1), now);
  }

  @Test
  void pendingProposalExpiresWhenJarUnlocks() {
    Instant unlock = Instant.parse("2026-12-31T00:00:00Z");
    assertThat(policy.derivedExpired(unlock, unlock)).isTrue();
  }

  @Test
  void pendingProposalCanBeRejectedOrCancelled() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    var rejected =
        new UnlockProposalEntity(UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(60), now);
    rejected.reject(now.plusSeconds(1));
    assertThat(rejected.getStatus()).isEqualTo(UnlockProposalEntity.Status.REJECTED);
    var cancelled =
        new UnlockProposalEntity(UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(60), now);
    cancelled.cancel(now.plusSeconds(1));
    assertThat(cancelled.getStatus()).isEqualTo(UnlockProposalEntity.Status.CANCELLED);
  }
}
