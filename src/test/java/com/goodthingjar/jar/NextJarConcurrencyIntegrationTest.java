package com.goodthingjar.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goodthingjar.jar.application.JarLifecycleService;
import com.goodthingjar.platform.error.ApiException;
import com.goodthingjar.support.PostgresIntegrationSupport;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NextJarConcurrencyIntegrationTest extends PostgresIntegrationSupport {
  @Autowired JarLifecycleService lifecycle;

  @Test
  void duplicateAndConcurrentRequestsCreateExactlyOneNextCurrentJar() throws Exception {
    User first = user("next-first@example.com");
    User second = user("next-second@example.com");
    PairFixture fixture = pair(first, second, "UTC");
    clock.set(
        jdbc.queryForObject(
            "select effective_unlock_at from shared_jar where id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            fixture.jarId()));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger created = new AtomicInteger();
    var conflicts = new ConcurrentLinkedQueue<String>();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstResult =
          executor.submit(() -> createAfterGate(first, ready, start, created, conflicts));
      var secondResult =
          executor.submit(() -> createAfterGate(second, ready, start, created, conflicts));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      firstResult.get(10, TimeUnit.SECONDS);
      secondResult.get(10, TimeUnit.SECONDS);
    }

    assertThat(created).as("concurrent conflicts: %s", conflicts).hasValue(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from shared_jar where pair_id=?", Integer.class, fixture.pairId()))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from shared_jar where pair_id=? and current",
                Integer.class,
                fixture.pairId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select sequence_number from shared_jar where pair_id=? and current",
                Integer.class,
                fixture.pairId()))
        .isEqualTo(2);
    assertThatThrownBy(() -> lifecycle.createNext(first.accountId()))
        .isInstanceOf(ApiException.class)
        .hasMessage("Current jar is still locked");
  }

  private void createAfterGate(
      User actor,
      CountDownLatch ready,
      CountDownLatch start,
      AtomicInteger created,
      ConcurrentLinkedQueue<String> conflicts) {
    ready.countDown();
    try {
      start.await();
      lifecycle.createNext(actor.accountId());
      created.incrementAndGet();
    } catch (ApiException expectedRaceLoser) {
      conflicts.add(expectedRaceLoser.getMessage());
      // The pair row lock serializes requests; the loser observes the new locked jar.
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
