package com.goodthingjar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodthingjar.jar.application.EntryQueryService;
import com.goodthingjar.jar.application.JarQueryService;
import com.goodthingjar.support.PostgresIntegrationSupport;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class QueryCountIntegrationTest extends PostgresIntegrationSupport {
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired JarQueryService jarQueries;
  @Autowired EntryQueryService entryQueries;

  @DynamicPropertySource
  static void hibernateStatistics(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
  }

  @Test
  void invitationListQueryCountDoesNotGrowWithRows() {
    User inviter = user("query-inviter@example.com");
    for (int i = 0; i < 40; i++) {
      invitations.create(inviter.accountId(), "query-target-" + i + "@example.com", "UTC");
    }

    Statistics statistics = resetStatistics();
    assertThat(invitations.list(inviter.accountId())).hasSize(40);
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
  }

  @Test
  void jarHistoryAndSettingsQueryCountsDoNotGrowWithHistory() {
    User first = user("query-history-first@example.com");
    User second = user("query-history-second@example.com");
    PairFixture fixture = pair(first, second, "UTC");
    for (int sequence = 2; sequence <= 40; sequence++) {
      jdbc.update(
          "insert into shared_jar(id,pair_id,sequence_number,current,time_zone,effective_unlock_at,created_at,version) values (?,?,?,false,'UTC',?,?,0)",
          UUID.randomUUID(),
          fixture.pairId(),
          sequence,
          Timestamp.from(clock.instant().minusSeconds(60)),
          Timestamp.from(clock.instant()));
    }

    Statistics historyStatistics = resetStatistics();
    assertThat(jarQueries.history(first.accountId())).hasSize(40);
    assertThat(historyStatistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);

    Statistics detailStatistics = resetStatistics();
    assertThat(jarQueries.detail(first.accountId(), fixture.jarId()).getId())
        .isEqualTo(fixture.jarId());
    assertThat(detailStatistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
  }

  @Test
  void unlockedEntryPageUsesConstantQueryCount() {
    User first = user("query-entry-first@example.com");
    User second = user("query-entry-second@example.com");
    PairFixture fixture = pair(first, second, "UTC");
    jdbc.update(
        "update shared_jar set effective_unlock_at=? where id=?",
        Timestamp.from(clock.instant().minusSeconds(1)),
        fixture.jarId());
    for (int i = 0; i < 250; i++) {
      jdbc.update(
          "insert into jar_entry(id,jar_id,author_account_id,text,created_at) values (?,?,?,?,?)",
          UUID.randomUUID(),
          fixture.jarId(),
          i % 2 == 0 ? first.accountId() : second.accountId(),
          "query-entry-" + i,
          Timestamp.from(clock.instant()));
    }

    Statistics statistics = resetStatistics();
    var page = entryQueries.page(first.accountId(), fixture.jarId(), null, 100);
    assertThat(page.items()).hasSize(100);
    assertThat(page.hasMore()).isTrue();
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
  }

  private Statistics resetStatistics() {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    return statistics;
  }
}
