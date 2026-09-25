package com.goodthingjar.jar;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodthingjar.jar.application.EntryQueryService;
import com.goodthingjar.support.PostgresIntegrationSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EntryPaginationIntegrationTest extends PostgresIntegrationSupport {
  @Autowired EntryQueryService queries;

  @Test
  void tenThousandEntriesAreReturnedExactlyOnceWithStableEqualTimestampOrdering() {
    User first = user("page-first@example.com");
    User second = user("page-second@example.com");
    PairFixture fixture = pair(first, second, "UTC");
    Instant base = Instant.parse("2026-07-01T00:00:00Z");
    Map<UUID, ExpectedEntry> expected = new HashMap<>();
    var batch = new ArrayList<Object[]>(10_000);
    for (int index = 0; index < 10_000; index++) {
      UUID id = UUID.randomUUID();
      UUID author = index % 2 == 0 ? first.accountId() : second.accountId();
      Instant createdAt = base.plusSeconds(index / 10);
      String text = "entry-" + index;
      expected.put(id, new ExpectedEntry(text, author, createdAt));
      batch.add(new Object[] {id, fixture.jarId(), author, text, Timestamp.from(createdAt)});
    }
    jdbc.batchUpdate(
        "insert into jar_entry(id,jar_id,author_account_id,text,created_at) values (?,?,?,?,?)",
        batch);
    Instant unlock =
        jdbc.queryForObject(
            "select effective_unlock_at from shared_jar where id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            fixture.jarId());
    clock.set(unlock);

    var seen = new HashSet<UUID>();
    String cursor = null;
    int pageCount = 0;
    do {
      var page = queries.page(first.accountId(), fixture.jarId(), cursor, 100);
      pageCount++;
      for (var entry : page.items()) {
        assertThat(seen.add(entry.getId())).isTrue();
        ExpectedEntry value = expected.get(entry.getId());
        assertThat(value).isNotNull();
        assertThat(entry.getText()).isEqualTo(value.text());
        assertThat(entry.getAuthorAccountId()).isEqualTo(value.author());
        assertThat(entry.getCreatedAt()).isEqualTo(value.createdAt());
      }
      cursor = page.nextCursor();
      if (!page.hasMore()) break;
      assertThat(cursor).isNotBlank();
    } while (pageCount < 101);

    assertThat(pageCount).isEqualTo(100);
    assertThat(seen).hasSize(10_000).containsExactlyInAnyOrderElementsOf(expected.keySet());
  }

  private record ExpectedEntry(String text, UUID author, Instant createdAt) {}
}
