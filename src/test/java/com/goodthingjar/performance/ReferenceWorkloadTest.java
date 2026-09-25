package com.goodthingjar.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodthingjar.jar.application.EntryCommandService;
import com.goodthingjar.jar.application.EntryQueryService;
import com.goodthingjar.support.PostgresIntegrationSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("performance")
class ReferenceWorkloadTest extends PostgresIntegrationSupport {
  @Autowired EntryCommandService entryCommands;
  @Autowired EntryQueryService entryQueries;

  @Test
  void referenceWorkloadAcceptsEveryWriteAndReadsTenThousandEntryJar() {
    List<Fixture> fixtures = seedOneHundredActivePairs();
    List<Duration> writeLatencies = new ArrayList<>();

    for (int i = 0; i < fixtures.size(); i++) {
      Fixture fixture = fixtures.get(i);
      long started = System.nanoTime();
      entryCommands.add(fixture.writer(), fixture.jar(), "reference-write-" + i);
      writeLatencies.add(Duration.ofNanos(System.nanoTime() - started));
    }

    Fixture large = fixtures.getFirst();
    seedEntries(large, 10_000);
    jdbc.update(
        "update shared_jar set effective_unlock_at=? where id=?",
        Timestamp.from(clock.instant()),
        large.jar());
    long readStarted = System.nanoTime();
    var firstPage = entryQueries.page(large.writer(), large.jar(), null, 100);
    Duration firstPageLatency = Duration.ofNanos(System.nanoTime() - readStarted);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from jar_entry where text like 'reference-write-%'",
                Integer.class))
        .isEqualTo(100);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from jar_entry where jar_id=?", Integer.class, large.jar()))
        .isEqualTo(10_001);
    assertThat(firstPage.items()).hasSize(100);
    assertThat(firstPage.hasMore()).isTrue();
    assertThat(percentile(writeLatencies, 0.95)).isLessThan(Duration.ofSeconds(2));
    assertThat(firstPageLatency).isLessThan(Duration.ofSeconds(3));
  }

  private List<Fixture> seedOneHundredActivePairs() {
    List<Fixture> fixtures = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      UUID first = UUID.randomUUID();
      UUID second = UUID.randomUUID();
      UUID pair = UUID.randomUUID();
      UUID jar = UUID.randomUUID();
      jdbc.update(
          "insert into account(id,email_normalized,password_hash,verified_at,created_at,version) values (?,?,?, ?,?,0),(?,?,?, ?,?,0)",
          first,
          "reference-first-" + i + "@example.test",
          "unused-hash",
          Timestamp.from(clock.instant()),
          Timestamp.from(clock.instant()),
          second,
          "reference-second-" + i + "@example.test",
          "unused-hash",
          Timestamp.from(clock.instant()),
          Timestamp.from(clock.instant()));
      jdbc.update(
          "insert into couple_pair(id,time_zone,created_at,version) values (?,'UTC',?,0)",
          pair,
          Timestamp.from(clock.instant()));
      jdbc.update(
          "insert into pair_member(pair_id,account_id,joined_at) values (?,?,?),(?,?,?)",
          pair,
          first,
          Timestamp.from(clock.instant()),
          pair,
          second,
          Timestamp.from(clock.instant()));
      jdbc.update(
          "insert into shared_jar(id,pair_id,sequence_number,current,time_zone,effective_unlock_at,created_at,version) values (?,?,1,true,'UTC',?,?,0)",
          jar,
          pair,
          Timestamp.from(clock.instant().plusSeconds(86400)),
          Timestamp.from(clock.instant()));
      fixtures.add(new Fixture(first, jar));
    }
    return fixtures;
  }

  private void seedEntries(Fixture fixture, int count) {
    List<Object[]> rows = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      rows.add(
          new Object[] {
            UUID.randomUUID(),
            fixture.jar(),
            fixture.writer(),
            "large-jar-entry-" + i,
            Timestamp.from(clock.instant())
          });
    }
    jdbc.batchUpdate(
        "insert into jar_entry(id,jar_id,author_account_id,text,created_at) values (?,?,?,?,?)",
        rows);
  }

  private Duration percentile(List<Duration> values, double percentile) {
    List<Duration> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
    int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile) - 1);
    return sorted.get(index);
  }

  private record Fixture(UUID writer, UUID jar) {}
}
