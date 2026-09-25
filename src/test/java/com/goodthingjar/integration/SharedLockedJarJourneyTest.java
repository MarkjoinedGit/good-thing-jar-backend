package com.goodthingjar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goodthingjar.support.PostgresIntegrationSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SharedLockedJarJourneyTest extends PostgresIntegrationSupport {

  @Test
  void authenticatedPairingWritingRevealHistoryAndNextJarJourney() throws Exception {
    User first = user("journey-first@example.com");
    User second = user("journey-second@example.com");

    String invitationBody =
        mvc.perform(
                post("/invitations")
                    .header("Authorization", bearer(first))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"journey-second@example.com\",\"timeZone\":\"America/New_York\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING_DELIVERY"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID invitationId = UUID.fromString(JsonPath.read(invitationBody, "$.id"));
    invitationDeliveries.recordResult(invitationId, true);

    mvc.perform(
            get("/invitations")
                .queryParam("direction", "incoming")
                .header("Authorization", bearer(second)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(invitationId.toString()))
        .andExpect(jsonPath("$[0].status").value("PENDING"));
    mvc.perform(
            post("/invitations/{id}/accept", invitationId).header("Authorization", bearer(second)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberAccountIds.length()").value(2));
    mvc.perform(get("/pairs/current").header("Authorization", bearer(first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeZone").value("America/New_York"));

    String jarsBody =
        mvc.perform(get("/jars").header("Authorization", bearer(first)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].lockStatus").value("LOCKED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID jarId = UUID.fromString(JsonPath.read(jarsBody, "$[0].id"));
    createEntry(first, jarId, "first memory");
    createEntry(second, jarId, "second memory");
    mvc.perform(get("/jars/{id}/entries", jarId).header("Authorization", bearer(first)))
        .andExpect(status().isLocked());

    clock.set(
        jdbc.queryForObject(
            "select effective_unlock_at from shared_jar where id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            jarId));
    first = reauthenticate(first);
    second = reauthenticate(second);
    String revealed =
        mvc.perform(
                get("/jars/{id}/entries", jarId)
                    .queryParam("limit", "1")
                    .header("Authorization", bearer(second)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(revealed, "$.nextCursor");
    String secondPage =
        mvc.perform(
                get("/jars/{id}/entries", jarId)
                    .queryParam("limit", "1")
                    .queryParam("cursor", cursor)
                    .header("Authorization", bearer(second)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(revealed + secondPage).contains("first memory", "second memory");

    mvc.perform(post("/jars").header("Authorization", bearer(first)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sequenceNumber").value(2))
        .andExpect(jsonPath("$.lockStatus").value("LOCKED"));
    mvc.perform(get("/jars").header("Authorization", bearer(second)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  private void createEntry(User actor, UUID jarId, String text) throws Exception {
    mvc.perform(
            post("/jars/{id}/entries", jarId)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }
}
