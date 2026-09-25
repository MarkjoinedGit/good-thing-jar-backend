package com.goodthingjar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.goodthingjar.jar.application.EntryCommandService;
import com.goodthingjar.notification.infrastructure.SmtpMailGateway;
import com.goodthingjar.notification.persistence.OutboxMessageRepository;
import com.goodthingjar.support.PostgresIntegrationSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SensitiveDataExposureTest extends PostgresIntegrationSupport {
  @Autowired OutboxMessageRepository outbox;
  @Autowired EntryCommandService entryCommands;
  @Autowired MeterRegistry meters;

  @Test
  void intendedOwnerMessageContainsVerificationSecret() {
    JavaMailSender sender = mock(JavaMailSender.class);
    SmtpMailGateway gateway = new SmtpMailGateway(sender);
    gateway.sendVerification("owner@example.com", "owner-only-secret");

    ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(message.capture());
    assertThat(message.getValue().getTo()).containsExactly("owner@example.com");
    assertThat(message.getValue().getText()).contains("owner-only-secret");
  }

  @Test
  void verificationAndAuthenticationSecretsAreNeverPersistedInPlaintext() {
    UUID account = registrations.register("secret-owner@example.com", PASSWORD);
    String encrypted =
        jdbc.queryForObject(
            "select encrypted_secret from outbox_message where aggregate_id=? and message_type='EMAIL_VERIFICATION'",
            String.class,
            account);
    String verificationToken = cipher.decrypt(encrypted);
    String verificationHash =
        jdbc.queryForObject(
            "select token_hash from email_verification where account_id=?", String.class, account);

    assertThat(verificationHash).hasSize(64).isNotEqualTo(verificationToken);
    assertThat(encrypted).doesNotContain(verificationToken);
    assertThat(
            jdbc.queryForObject(
                "select payload from outbox_message where aggregate_id=? and message_type='EMAIL_VERIFICATION'",
                String.class,
                account))
        .doesNotContain(verificationToken);

    verifications.verify(verificationToken);
    var tokens = sessions.create("secret-owner@example.com", PASSWORD, "raw-auth-scope");
    String persistedSession =
        jdbc.queryForObject(
            "select access_token_hash || ':' || refresh_token_hash from auth_session where account_id=?",
            String.class,
            account);
    assertThat(persistedSession)
        .doesNotContain(tokens.accessToken())
        .doesNotContain(tokens.refreshToken())
        .hasSize(129);
    assertThat(
            jdbc.queryForObject(
                "select scope_hash from abuse_throttle_bucket where operation='authentication'",
                String.class))
        .doesNotContain("raw-auth-scope")
        .doesNotContain("secret-owner@example.com");

    var message =
        outbox.findAll().stream()
            .filter(row -> "EMAIL_VERIFICATION".equals(row.getMessageType()))
            .findFirst()
            .orElseThrow();
    message.delivered(clock.instant());
    outbox.saveAndFlush(message);
    assertThat(
            jdbc.queryForObject(
                "select encrypted_secret from outbox_message where id=?",
                String.class,
                message.getId()))
        .isNull();
  }

  @Test
  void entryTextEmailAndScopesStayOutOfProblemsAuditOutboxAndMetrics() throws Exception {
    User first = user("private-first@example.com");
    User second = user("private-second@example.com");
    User outsider = user("private-outsider@example.com");
    PairFixture fixture = pair(first, second, "UTC");
    String privateEntry = "private-entry-marker-7d2e";
    entryCommands.add(first.accountId(), fixture.jarId(), privateEntry);

    String lockedProblem =
        mvc.perform(
                get("/jars/{jarId}/entries", fixture.jarId())
                    .header("Authorization", bearer(first)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String hiddenProblem =
        mvc.perform(
                get("/jars/{jarId}/entries", fixture.jarId())
                    .header("Authorization", bearer(outsider)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(lockedProblem)
        .doesNotContain(privateEntry)
        .doesNotContain(first.email())
        .doesNotContain(first.accessToken());
    assertThat(hiddenProblem)
        .doesNotContain(privateEntry)
        .doesNotContain(outsider.email())
        .doesNotContain(outsider.accessToken());

    String diagnosticPersistence =
        String.join(
                "|",
                jdbc.queryForList(
                    "select coalesce(event_type,'') || ':' || coalesce(actor_scope,'') || ':' || coalesce(outcome,'') || ':' || coalesce(correlation_id,'') from security_audit_event",
                    String.class))
            + "|"
            + String.join(
                "|",
                jdbc.queryForList(
                    "select coalesce(payload,'') || ':' || coalesce(encrypted_secret,'') || ':' || coalesce(last_error_code,'') from outbox_message",
                    String.class))
            + "|"
            + String.join(
                "|",
                jdbc.queryForList("select scope_hash from abuse_throttle_bucket", String.class));
    assertThat(diagnosticPersistence)
        .doesNotContain(privateEntry)
        .doesNotContain(first.accessToken())
        .doesNotContain(first.email());

    String meterMetadata =
        meters.getMeters().stream()
            .map(meter -> meter.getId().getName() + meter.getId().getTags())
            .reduce("", (left, right) -> left + right);
    assertThat(meterMetadata)
        .doesNotContain(privateEntry)
        .doesNotContain(first.email())
        .doesNotContain(first.accessToken());

    assertThat(
            jdbc.queryForObject(
                "select text from jar_entry where jar_id=?", String.class, fixture.jarId()))
        .isEqualTo(privateEntry);
  }
}
