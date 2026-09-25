package com.goodthingjar.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goodthingjar.jar.application.UnlockProposalService;
import com.goodthingjar.platform.error.ApiException;
import com.goodthingjar.support.PostgresIntegrationSupport;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class UnlockProposalIntegrationTest extends PostgresIntegrationSupport {
  @Autowired UnlockProposalService service;

  @Test
  void concurrentCreationLeavesExactlyOnePendingProposal() throws Exception {
    User first = user("proposal-race-first@example.com");
    User second = user("proposal-race-second@example.com");
    PairFixture fixture = pair(first, second, "UTC");
    AtomicInteger successes = new AtomicInteger();

    runConcurrently(
        () -> propose(first, fixture.jarId(), clock.instant().plusSeconds(7200), successes),
        () -> propose(second, fixture.jarId(), clock.instant().plusSeconds(10800), successes));

    assertThat(successes).hasValue(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from unlock_proposal where jar_id=? and status='PENDING'",
                Integer.class,
                fixture.jarId()))
        .isEqualTo(1);
  }

  @Test
  void otherPartnerApprovalAtomicallyUpdatesJarAndProposal() throws Exception {
    User proposer = user("proposal-approve-first@example.com");
    User approver = user("proposal-approve-second@example.com");
    PairFixture fixture = pair(proposer, approver, "UTC");
    Instant proposed = clock.instant().plusSeconds(7200);
    var proposal = service.propose(proposer.accountId(), fixture.jarId(), proposed);

    mvc.perform(
            post(
                    "/jars/{jarId}/unlock-proposals/{proposalId}/approval",
                    fixture.jarId(),
                    proposal.getId())
                .header("Authorization", bearer(approver)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectiveUnlockAt").value(proposed.toString()));

    assertThat(
            jdbc.queryForObject(
                "select effective_unlock_at from shared_jar where id=?",
                Instant.class,
                fixture.jarId()))
        .isEqualTo(proposed);
    assertThat(statusOf(proposal.getId())).isEqualTo("APPROVED");
  }

  @Test
  void exactUnlockMakesApprovalStaleAndPersistsDerivedExpiry() {
    User proposer = user("proposal-stale-first@example.com");
    User approver = user("proposal-stale-second@example.com");
    PairFixture fixture = pair(proposer, approver, "UTC");
    var proposal =
        service.propose(
            proposer.accountId(), fixture.jarId(), clock.instant().plusSeconds(86400 * 2));
    Instant originalUnlock =
        jdbc.queryForObject(
            "select effective_unlock_at from shared_jar where id=?",
            Instant.class,
            fixture.jarId());

    clock.set(originalUnlock);
    assertThatThrownBy(
            () -> service.approve(approver.accountId(), fixture.jarId(), proposal.getId()))
        .isInstanceOf(ApiException.class);

    assertThat(statusOf(proposal.getId())).isEqualTo("EXPIRED");
    assertThat(
            jdbc.queryForObject(
                "select effective_unlock_at from shared_jar where id=?",
                Instant.class,
                fixture.jarId()))
        .isEqualTo(originalUnlock);
  }

  @Test
  void proposerCannotApproveAndResolutionEndpointsPreserveTheJarUntilApproval() throws Exception {
    User proposer = user("proposal-resolve-first@example.com");
    User partner = user("proposal-resolve-second@example.com");
    PairFixture fixture = pair(proposer, partner, "UTC");
    Instant originalUnlock =
        jdbc.queryForObject(
            "select effective_unlock_at from shared_jar where id=?",
            Instant.class,
            fixture.jarId());

    var rejected =
        service.propose(proposer.accountId(), fixture.jarId(), clock.instant().plusSeconds(60));
    assertThatThrownBy(
            () -> service.approve(proposer.accountId(), fixture.jarId(), rejected.getId()))
        .isInstanceOf(ApiException.class);
    mvc.perform(
            post(
                    "/jars/{jarId}/unlock-proposals/{proposalId}/rejection",
                    fixture.jarId(),
                    rejected.getId())
                .header("Authorization", bearer(partner)))
        .andExpect(status().isNoContent());
    assertThat(statusOf(rejected.getId())).isEqualTo("REJECTED");

    var cancelled =
        service.propose(proposer.accountId(), fixture.jarId(), clock.instant().plusSeconds(120));
    mvc.perform(
            delete(
                    "/jars/{jarId}/unlock-proposals/{proposalId}",
                    fixture.jarId(),
                    cancelled.getId())
                .header("Authorization", bearer(proposer)))
        .andExpect(status().isNoContent());
    assertThat(statusOf(cancelled.getId())).isEqualTo("CANCELLED");
    assertThat(
            jdbc.queryForObject(
                "select effective_unlock_at from shared_jar where id=?",
                Instant.class,
                fixture.jarId()))
        .isEqualTo(originalUnlock);
  }

  @Test
  void nonmemberAndNonexistentJarUseTheSamePrivacySafeProblem() throws Exception {
    User first = user("proposal-private-first@example.com");
    User second = user("proposal-private-second@example.com");
    User outsider = user("proposal-private-outsider@example.com");
    PairFixture fixture = pair(first, second, "UTC");
    String body = "{\"proposedUnlockAt\":\"" + clock.instant().plusSeconds(7200) + "\"}";

    mvc.perform(
            post("/jars/{jarId}/unlock-proposals", fixture.jarId())
                .header("Authorization", bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource_not_found"));
    mvc.perform(
            post("/jars/{jarId}/unlock-proposals", UUID.randomUUID())
                .header("Authorization", bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource_not_found"));
  }

  @Test
  void pendingProposalAppearsInJarDetailWithoutChangingEffectiveUnlock() throws Exception {
    User proposer = user("proposal-detail-first@example.com");
    User partner = user("proposal-detail-second@example.com");
    PairFixture fixture = pair(proposer, partner, "UTC");
    Instant proposed = clock.instant().plusSeconds(7200);
    var proposal = service.propose(proposer.accountId(), fixture.jarId(), proposed);

    mvc.perform(get("/jars/{jarId}", fixture.jarId()).header("Authorization", bearer(partner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pendingUnlockProposal.id").value(proposal.getId().toString()))
        .andExpect(jsonPath("$.pendingUnlockProposal.proposedUnlockAt").value(proposed.toString()))
        .andExpect(jsonPath("$.lockStatus").value("LOCKED"));
  }

  private String statusOf(UUID proposalId) {
    return jdbc.queryForObject(
        "select status from unlock_proposal where id=?", String.class, proposalId);
  }

  private void propose(User user, UUID jarId, Instant proposed, AtomicInteger successes) {
    try {
      service.propose(user.accountId(), jarId, proposed);
      successes.incrementAndGet();
    } catch (RuntimeException expectedRaceLoser) {
      // The database partial unique index leaves exactly one pending proposal.
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
