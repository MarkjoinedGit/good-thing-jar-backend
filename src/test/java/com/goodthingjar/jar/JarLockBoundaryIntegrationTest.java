package com.goodthingjar.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goodthingjar.jar.application.EntryCommandService;
import com.goodthingjar.jar.application.EntryQueryService;
import com.goodthingjar.jar.domain.DefaultUnlockCalculator;
import com.goodthingjar.jar.domain.JarLockPolicy;
import com.goodthingjar.platform.error.ApiException;
import com.goodthingjar.support.PostgresIntegrationSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JarLockBoundaryIntegrationTest extends PostgresIntegrationSupport {
  @Autowired EntryCommandService entryCommands;
  @Autowired EntryQueryService entryQueries;

  @Test
  void oneInstantBeforeIsPrivateAndWritableButExactInstantRevealsAndBecomesReadOnly() {
    clock.set(Instant.parse("2026-06-01T12:00:00Z"));
    User first = user("boundary-first@example.com");
    User second = user("boundary-second@example.com");
    PairFixture fixture = pair(first, second, "America/New_York");
    Instant unlock =
        jdbc.queryForObject(
            "select effective_unlock_at from shared_jar where id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            fixture.jarId());

    clock.set(unlock.minusNanos(1));
    entryCommands.add(first.accountId(), fixture.jarId(), "last locked instant");
    assertThatThrownBy(() -> entryQueries.page(first.accountId(), fixture.jarId(), null, 50))
        .isInstanceOf(ApiException.class)
        .hasMessage("Jar is locked");

    clock.set(unlock);
    var page = entryQueries.page(second.accountId(), fixture.jarId(), null, 50);
    assertThat(page.items())
        .singleElement()
        .extracting(e -> e.getText())
        .isEqualTo("last locked instant");
    assertThatThrownBy(() -> entryCommands.add(second.accountId(), fixture.jarId(), "too late"))
        .isInstanceOf(ApiException.class)
        .hasMessage("Jar is read-only");
  }

  @Test
  void defaultUsesNextCalendarYearInTheFixedZoneAcrossDstAndYearRollover() {
    var calculator = new DefaultUnlockCalculator();
    var policy = new JarLockPolicy();
    Instant utcCreation = Instant.parse("2026-12-31T23:59:59Z");
    Instant utcUnlock = calculator.calculate(utcCreation, "UTC");
    assertThat(utcUnlock).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    assertThat(policy.isLocked(utcUnlock.minusNanos(1), utcUnlock)).isTrue();
    assertThat(policy.isLocked(utcUnlock, utcUnlock)).isFalse();

    assertThat(calculator.calculate(Instant.parse("2026-03-08T06:59:59Z"), "America/New_York"))
        .isEqualTo(Instant.parse("2027-01-01T05:00:00Z"));
    assertThat(calculator.calculate(Instant.parse("2026-11-01T05:59:59Z"), "America/New_York"))
        .isEqualTo(Instant.parse("2027-01-01T05:00:00Z"));
  }
}
