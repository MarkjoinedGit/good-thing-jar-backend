package com.goodthingjar.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goodthingjar.pairing.domain.InvitationStatus;
import com.goodthingjar.platform.error.ApiException;
import com.goodthingjar.support.PostgresIntegrationSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class InvitationLifecycleApiTest extends PostgresIntegrationSupport {

  @DynamicPropertySource
  static void invitationThrottle(DynamicPropertyRegistry registry) {
    registry.add("gtj.throttle.invitation-create.capacity", () -> "2");
  }

  @Test
  void validatesEmailAndIanaZoneWithoutDisclosingTargetAccountState() throws Exception {
    User inviter = user("inviter@example.com");
    user("registered-target@example.com");

    mvc.perform(
            post("/invitations")
                .header("Authorization", bearer(inviter))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"target@example.com\",\"timeZone\":\"Not/A_Zone\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"));
    mvc.perform(
            post("/invitations")
                .header("Authorization", bearer(inviter))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"timeZone\":\"UTC\"}"))
        .andExpect(status().isBadRequest());
    jdbc.update("delete from abuse_throttle_bucket");
    mvc.perform(
            post("/invitations")
                .header("Authorization", bearer(inviter))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"registered-target@example.com\",\"timeZone\":\"UTC\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING_DELIVERY"));
    mvc.perform(
            post("/invitations")
                .header("Authorization", bearer(inviter))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"absent@example.com\",\"timeZone\":\"UTC\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING_DELIVERY"));
    mvc.perform(
            post("/invitations")
                .header("Authorization", bearer(inviter))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"third@example.com\",\"timeZone\":\"UTC\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("temporarily_throttled"));
  }

  @Test
  void retryKeepsTheExactSevenDayDeadlineAndLateDeliveryCannotReactivateCancellation()
      throws Exception {
    User inviter = user("owner@example.com");
    var invitation = invitations.create(inviter.accountId(), "target@example.com", "UTC");
    assertThat(invitation.getExpiresAt())
        .isEqualTo(invitation.getCreatedAt().plus(Duration.ofDays(7)));
    invitationDeliveries.recordResult(invitation.getId(), false);
    var originalExpiry = invitation.getExpiresAt();
    var retried = invitations.retry(inviter.accountId(), invitation.getId());
    assertThat(retried.getId()).isEqualTo(invitation.getId());
    assertThat(retried.getExpiresAt()).isEqualTo(originalExpiry);
    assertThat(retried.getStatus()).isEqualTo(InvitationStatus.PENDING_DELIVERY);

    invitations.cancel(inviter.accountId(), invitation.getId());
    invitationDeliveries.recordResult(invitation.getId(), true);
    assertThat(invitationStatus(invitation.getId())).isEqualTo("CANCELLED");
    assertThatThrownBy(() -> invitations.retry(inviter.accountId(), invitation.getId()))
        .isInstanceOf(ApiException.class);

    mvc.perform(
            get("/invitations")
                .queryParam("direction", "outgoing")
                .header("Authorization", bearer(inviter)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].direction").value("OUTGOING"));
    mvc.perform(get("/invitations").header("Authorization", bearer(inviter)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exactExpiryIsPersistedAndReleasesTheDuplicateGuard() {
    User inviter = user("expiry-owner@example.com");
    var invitation = invitations.create(inviter.accountId(), "same@example.com", "UTC");
    clock.set(invitation.getExpiresAt().minusNanos(1));
    assertThat(invitationDeliveries.mayDeliver(invitation.getId())).isTrue();
    clock.set(invitation.getExpiresAt());
    assertThat(invitationDeliveries.mayDeliver(invitation.getId())).isFalse();
    assertThat(invitationStatus(invitation.getId())).isEqualTo("EXPIRED");
    assertThat(invitations.create(inviter.accountId(), "same@example.com", "UTC").getId())
        .isNotEqualTo(invitation.getId());
  }

  @Test
  void cancellationWorksFromEveryActiveStateAndIsTerminal() {
    User inviter = user("cancel-owner@example.com");
    for (InvitationStatus active :
        java.util.List.of(
            InvitationStatus.PENDING_DELIVERY,
            InvitationStatus.PENDING,
            InvitationStatus.DELIVERY_FAILED)) {
      String target = active.name().toLowerCase() + "@example.com";
      var invitation = invitations.create(inviter.accountId(), target, "UTC");
      if (active == InvitationStatus.PENDING) {
        invitationDeliveries.recordResult(invitation.getId(), true);
      } else if (active == InvitationStatus.DELIVERY_FAILED) {
        invitationDeliveries.recordResult(invitation.getId(), false);
      }
      invitations.cancel(inviter.accountId(), invitation.getId());
      assertThat(invitationStatus(invitation.getId())).isEqualTo("CANCELLED");
      invitationDeliveries.recordResult(invitation.getId(), true);
      assertThat(invitationStatus(invitation.getId())).isEqualTo("CANCELLED");
      assertThat(invitations.create(inviter.accountId(), target, "UTC").getId())
          .isNotEqualTo(invitation.getId());
      jdbc.update("delete from abuse_throttle_bucket");
    }
  }

  @Test
  void duplicateCreationIsRejectedWithoutExtraInvitationOrOutboxMessage() {
    User inviter = user("duplicate-owner@example.com");
    invitations.create(inviter.accountId(), "duplicate@example.com", "UTC");
    assertThatThrownBy(
            () -> invitations.create(inviter.accountId(), "DUPLICATE@example.com", "UTC"))
        .isInstanceOf(ApiException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from invitation where inviter_account_id=?",
                Integer.class,
                inviter.accountId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_message where message_type='INVITATION'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void onlyTheInviterCanCancel() throws Exception {
    User inviter = user("cancel-inviter@example.com");
    User other = user("cancel-other@example.com");
    var invitation = invitations.create(inviter.accountId(), "target@example.com", "UTC");
    mvc.perform(
            delete("/invitations/{id}", invitation.getId()).header("Authorization", bearer(other)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource_not_found"));
  }

  private String invitationStatus(UUID invitationId) {
    return jdbc.queryForObject(
        "select status from invitation where id=?", String.class, invitationId);
  }
}
