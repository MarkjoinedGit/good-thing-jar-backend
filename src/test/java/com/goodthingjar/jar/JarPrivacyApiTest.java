package com.goodthingjar.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goodthingjar.support.PostgresIntegrationSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class JarPrivacyApiTest extends PostgresIntegrationSupport {

  @DynamicPropertySource
  static void entryThrottle(DynamicPropertyRegistry registry) {
    registry.add("gtj.throttle.entry-write.capacity", () -> "100");
    registry.add("gtj.throttle.entry-write.window", () -> "PT1M");
  }

  @Test
  void lockedResponsesRevealNoEntryMetadataAndNonmemberMatchesNonexistent() throws Exception {
    User first = user("privacy-first@example.com");
    User second = user("privacy-second@example.com");
    User outsider = user("privacy-outsider@example.com");
    PairFixture fixture = pair(first, second, "UTC");

    createEntry(first, fixture.jarId(), "private alpha");
    createEntry(second, fixture.jarId(), "private beta");
    String locked =
        mvc.perform(
                get("/jars/{jarId}/entries", fixture.jarId())
                    .header("Authorization", bearer(first)))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.code").value("jar_locked"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(locked)
        .doesNotContain(
            "private alpha", "private beta", "author", "count", "createdAt", "location");

    String hidden =
        mvc.perform(
                get("/jars/{jarId}/entries", fixture.jarId())
                    .header("Authorization", bearer(outsider)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String absent =
        mvc.perform(
                get("/jars/{jarId}/entries", UUID.randomUUID())
                    .header("Authorization", bearer(outsider)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(hidden).contains("\"code\":\"resource_not_found\"");
    assertThat(absent).contains("\"code\":\"resource_not_found\"");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_audit_event where event_type='protected_resource_denial'",
                Integer.class))
        .isEqualTo(3);
    assertThat(jdbc.queryForList("select actor_scope from security_audit_event", String.class))
        .allSatisfy(
            scope -> assertThat(scope).hasSize(64).doesNotContain(outsider.accountId().toString()));
  }

  @Test
  void shortWindowThrottleIsRetryableAndDoesNotCreateADailyQuota() throws Exception {
    User first = user("volume-first@example.com");
    User second = user("volume-second@example.com");
    PairFixture fixture = pair(first, second, "UTC");

    for (int index = 0; index < 100; index++) {
      createEntry(first, fixture.jarId(), "entry " + index);
    }
    mvc.perform(
            post("/jars/{jarId}/entries", fixture.jarId())
                .header("Authorization", bearer(first))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"throttled\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(jsonPath("$.code").value("temporarily_throttled"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from jar_entry where jar_id=?", Integer.class, fixture.jarId()))
        .isEqualTo(100);

    clock.set(clock.instant().plus(Duration.ofSeconds(60)));
    createEntry(first, fixture.jarId(), "retry succeeds");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from jar_entry where jar_id=?", Integer.class, fixture.jarId()))
        .isEqualTo(101);
  }

  @Test
  void entryAcknowledgementIsEmptyAndValidationIsBounded() throws Exception {
    User first = user("validation-first@example.com");
    User second = user("validation-second@example.com");
    PairFixture fixture = pair(first, second, "UTC");
    mvc.perform(
            post("/jars/{jarId}/entries", fixture.jarId())
                .header("Authorization", bearer(first))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"accepted\"}"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    mvc.perform(
            post("/jars/{jarId}/entries", fixture.jarId())
                .header("Authorization", bearer(first))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  private void createEntry(User user, UUID jarId, String text) throws Exception {
    mvc.perform(
            post("/jars/{jarId}/entries", jarId)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }
}
