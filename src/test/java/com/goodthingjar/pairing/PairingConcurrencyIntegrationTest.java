package com.goodthingjar.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodthingjar.support.PostgresIntegrationSupport;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PairingConcurrencyIntegrationTest extends PostgresIntegrationSupport {

  @Test
  void concurrentDuplicateInvitationCreationPersistsOneInvitationAndOneDelivery() throws Exception {
    User inviter = user("duplicate-race-owner@example.com");
    AtomicInteger successes = new AtomicInteger();
    runConcurrently(
        () -> {
          try {
            invitations.create(inviter.accountId(), "same-target@example.com", "UTC");
            successes.incrementAndGet();
          } catch (RuntimeException expectedRaceLoser) {
            // The partial unique index is the final concurrent duplicate guard.
          }
        },
        () -> {
          try {
            invitations.create(inviter.accountId(), "SAME-TARGET@example.com", "UTC");
            successes.incrementAndGet();
          } catch (RuntimeException expectedRaceLoser) {
            // The losing transaction is normalized to a conflict.
          }
        });

    assertThat(successes).hasValue(1);
    assertThat(jdbc.queryForObject("select count(*) from invitation", Integer.class)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_message where message_type='INVITATION'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void concurrentAcceptancesCreateOnePairPerAccountAndInvalidateAllOtherInvitations()
      throws Exception {
    User first = user("first@example.com");
    User second = user("second@example.com");
    User target = user("target@example.com");
    User fourth = user("fourth@example.com");

    var firstIncoming = invitations.create(first.accountId(), target.email(), "UTC");
    var secondIncoming = invitations.create(second.accountId(), target.email(), "UTC");
    var targetOutgoing = invitations.create(target.accountId(), fourth.email(), "UTC");
    List.of(firstIncoming, secondIncoming, targetOutgoing)
        .forEach(i -> invitationDeliveries.recordResult(i.getId(), true));

    AtomicInteger accepted = new AtomicInteger();
    runConcurrently(
        () -> accept(target, firstIncoming.getId(), accepted),
        () -> accept(target, secondIncoming.getId(), accepted));

    assertThat(accepted).hasValue(1);
    assertThat(jdbc.queryForObject("select count(*) from couple_pair", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from pair_member", Integer.class)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from pair_member where account_id=?",
                Integer.class,
                target.accountId()))
        .isEqualTo(1);
    assertThat(jdbc.queryForList("select status from invitation order by status", String.class))
        .containsExactlyInAnyOrder("ACCEPTED", "INVALIDATED", "INVALIDATED");
    assertThat(jdbc.queryForObject("select count(*) from shared_jar", Integer.class)).isEqualTo(1);
  }

  private void accept(User target, java.util.UUID invitationId, AtomicInteger accepted) {
    try {
      pairing.accept(target.accountId(), invitationId);
      accepted.incrementAndGet();
    } catch (RuntimeException expectedRaceLoser) {
      // Exactly one transaction may claim the globally unique membership.
    }
  }

  private void runConcurrently(Runnable first, Runnable second) throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> one = executor.submit(gated(ready, start, first));
      Future<?> two = executor.submit(gated(ready, start, second));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      one.get(15, TimeUnit.SECONDS);
      two.get(15, TimeUnit.SECONDS);
    }
  }

  private Runnable gated(CountDownLatch ready, CountDownLatch start, Runnable action) {
    return () -> {
      ready.countDown();
      try {
        start.await();
        action.run();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(interrupted);
      }
    };
  }
}
