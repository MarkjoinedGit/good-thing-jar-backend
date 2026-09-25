package com.goodthingjar.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goodthingjar.identity.application.EmailVerificationService;
import com.goodthingjar.notification.application.DeliverySecretCipher;
import com.goodthingjar.notification.application.InvitationDeliveryPort;
import com.goodthingjar.notification.application.OutboxDispatcher;
import com.goodthingjar.notification.infrastructure.SmtpMailGateway;
import com.goodthingjar.notification.persistence.OutboxMessageRepository;
import com.goodthingjar.platform.error.ThrottledException;
import com.goodthingjar.platform.security.SecretHasher;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "gtj.outbox.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthenticationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("gtj.throttle.authentication.capacity", () -> "2");
    registry.add("gtj.throttle.verification-resend.capacity", () -> "2");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired DeliverySecretCipher cipher;
  @Autowired EmailVerificationService verifications;
  @Autowired SecretHasher secretHasher;
  @Autowired OutboxMessageRepository outbox;
  @Autowired ObjectProvider<InvitationDeliveryPort> invitationDelivery;
  @Autowired Clock clock;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  void clean() {
    jdbc.update("delete from abuse_throttle_bucket");
    jdbc.update("delete from unlock_proposal");
    jdbc.update("delete from jar_entry");
    jdbc.update("delete from shared_jar");
    jdbc.update("delete from pair_member");
    jdbc.update("delete from couple_pair");
    jdbc.update("delete from invitation");
    jdbc.update("delete from outbox_message");
    jdbc.update("delete from auth_session");
    jdbc.update("delete from email_verification");
    jdbc.update("delete from account");
  }

  @Test
  void registrationVerificationRotationReuseAndLogoutAreEnforced() throws Exception {
    Registered account = register("person@example.com");
    assertThat(account.registrationBody()).doesNotContain(account.verificationToken());

    mvc.perform(
            post("/auth/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", account.verificationToken())))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    String sessionBody = createSession("person@example.com");
    String access = JsonPath.read(sessionBody, "$.accessToken");
    String refresh = JsonPath.read(sessionBody, "$.refreshToken");

    String rotatedBody =
        mvc.perform(
                post("/auth/sessions/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("refreshToken", refresh)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String rotatedAccess = JsonPath.read(rotatedBody, "$.accessToken");

    mvc.perform(
            post("/auth/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", refresh)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("authentication_required"));

    mvc.perform(delete("/auth/sessions/current").header("Authorization", "Bearer " + rotatedAccess))
        .andExpect(status().isUnauthorized());

    String secondSession = createSession("person@example.com");
    String secondAccess = JsonPath.read(secondSession, "$.accessToken");
    mvc.perform(delete("/auth/sessions/current").header("Authorization", "Bearer " + secondAccess))
        .andExpect(status().isNoContent());
    mvc.perform(post("/jars").header("Authorization", "Bearer " + secondAccess))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("authentication_required"));

    assertThat(access).isNotEqualTo(rotatedAccess);
    assertThat(jdbc.queryForObject("select count(*) from auth_session", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void resendIsGenericSupersedesTokensAndLeavesInvitationExpiryUntouched() throws Exception {
    Registered inviter = register("inviter@example.com");
    Registered invitee = register("invitee@example.com");
    Instant expiry = Instant.now().plusSeconds(3600);
    jdbc.update(
        "insert into invitation(id,inviter_account_id,target_email_normalized,time_zone,status,created_at,expires_at,version) values (?,?,?,?,?,?,?,0)",
        UUID.randomUUID(),
        inviter.accountId(),
        "invitee@example.com",
        "UTC",
        "PENDING",
        Timestamp.from(expiry.minusSeconds(7 * 24 * 60 * 60)),
        Timestamp.from(expiry));

    assertGenericResend("invitee@example.com");
    assertGenericResend("absent@example.com");

    String replacement = latestVerificationToken(invitee.accountId());
    assertThat(replacement).isNotEqualTo(invitee.verificationToken());
    assertThatThrownBy(() -> verifications.verify(invitee.verificationToken()))
        .hasMessage("Verification token is invalid or expired");
    verifications.verify(replacement);

    Instant storedExpiry =
        jdbc.queryForObject(
            "select expires_at from invitation where inviter_account_id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            inviter.accountId());
    assertThat(storedExpiry).isCloseTo(expiry, within(1, ChronoUnit.MICROS));
    assertGenericResend("invitee@example.com");
  }

  @Test
  void concurrentResendsLeaveOneNewestUsableToken() throws Exception {
    Registered account = register("concurrent@example.com");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      for (int index = 0; index < 2; index++) {
        int attempt = index;
        executor.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                verifications.resend("concurrent@example.com", "concurrent-" + attempt);
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
              } finally {
                finished.countDown();
              }
            });
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(
            jdbc.queryForObject(
                "select count(*) from email_verification where account_id=? and consumed_at is null and superseded_at is null",
                Integer.class,
                account.accountId()))
        .isEqualTo(1);
    verifications.verify(activeVerificationToken(account.accountId()));
  }

  @Test
  void verificationExpiresAtExactlyTwentyFourHours() throws Exception {
    Registered account = register("expired@example.com");
    jdbc.update(
        "update email_verification set issued_at=now()-interval '24 hours', expires_at=now() where account_id=?",
        account.accountId());

    mvc.perform(
            post("/auth/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", account.verificationToken())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"));
  }

  @Test
  void verificationOutboxDeliversReliablyAndSanitizesTerminalRows() throws Exception {
    Registered delivered = register("delivered@example.com");
    Registered failed = register("failed@example.com");
    SmtpMailGateway mail = mock(SmtpMailGateway.class);
    doAnswer(
            invocation -> {
              String recipient = invocation.getArgument(0);
              String token = invocation.getArgument(1);
              if (recipient.equals("failed@example.com")) {
                throw new IllegalStateException("mail failure " + recipient + " " + token);
              }
              return null;
            })
        .when(mail)
        .sendVerification(anyString(), anyString());
    OutboxDispatcher dispatcher =
        new OutboxDispatcher(
            outbox,
            mail,
            cipher,
            invitationDelivery,
            clock,
            new TransactionTemplate(transactionManager),
            Duration.ofMinutes(5));

    dispatcher.dispatch();

    verify(mail).sendVerification("delivered@example.com", delivered.verificationToken());
    verify(mail).sendVerification("failed@example.com", failed.verificationToken());
    assertTerminalOutbox(delivered.accountId(), "DELIVERED", null);
    assertTerminalOutbox(failed.accountId(), "FAILED", "delivery_failed");

    dispatcher.dispatch();
    assertThat(jdbc.queryForObject("select sum(attempts) from outbox_message", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void authenticationAndResendThrottlesPersistFailedAttempts() throws Exception {
    for (int attempt = 0; attempt < 2; attempt++) {
      mvc.perform(
              post("/auth/sessions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"missing@example.com\",\"password\":\"bad\"}"))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(
            post("/auth/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.com\",\"password\":\"bad\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(
            header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
        .andExpect(jsonPath("$.code").value("temporarily_throttled"));

    verifications.resend("throttled@example.com", "same-client");
    verifications.resend("throttled@example.com", "same-client");
    assertThatThrownBy(() -> verifications.resend("throttled@example.com", "same-client"))
        .isInstanceOf(ThrottledException.class)
        .extracting("retryAfterSeconds")
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LONG)
        .isPositive();
  }

  private Registered register(String email) throws Exception {
    String body =
        mvc.perform(
                post("/auth/registrations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"%s\",\"password\":\"correct horse battery staple\"}"
                            .formatted(email)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.verificationRequired").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID accountId = UUID.fromString(JsonPath.read(body, "$.accountId"));
    return new Registered(accountId, latestVerificationToken(accountId), body);
  }

  private String createSession(String email) throws Exception {
    return mvc.perform(
            post("/auth/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"correct horse battery staple\"}"
                        .formatted(email)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void assertGenericResend(String email) throws Exception {
    mvc.perform(
            post("/auth/email-verification-resends")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email)))
        .andExpect(status().isAccepted())
        .andExpect(content().json("{\"accepted\":true}"));
  }

  private String latestVerificationToken(UUID accountId) {
    String encrypted =
        jdbc.queryForObject(
            "select encrypted_secret from outbox_message where aggregate_id=? and message_type='EMAIL_VERIFICATION' order by created_at desc limit 1",
            String.class,
            accountId);
    assertThat(encrypted).isNotBlank();
    return cipher.decrypt(encrypted);
  }

  private String activeVerificationToken(UUID accountId) {
    String activeHash =
        jdbc.queryForObject(
            "select token_hash from email_verification where account_id=? and consumed_at is null and superseded_at is null",
            String.class,
            accountId);
    return jdbc
        .queryForList(
            "select encrypted_secret from outbox_message where aggregate_id=? and message_type='EMAIL_VERIFICATION'",
            String.class,
            accountId)
        .stream()
        .map(cipher::decrypt)
        .filter(token -> secretHasher.hashToken(token).equals(activeHash))
        .findFirst()
        .orElseThrow();
  }

  private void assertTerminalOutbox(UUID accountId, String status, String errorCode) {
    var row =
        jdbc.queryForMap(
            "select status, encrypted_secret, last_error_code, payload from outbox_message where aggregate_id=?",
            accountId);
    assertThat(row.get("status")).isEqualTo(status);
    assertThat(row.get("encrypted_secret")).isNull();
    assertThat(row.get("last_error_code")).isEqualTo(errorCode);
    assertThat(row.get("payload").toString()).doesNotContain("@example.com");
  }

  private static String json(String field, String value) {
    return "{\"" + field + "\":\"" + value + "\"}";
  }

  private record Registered(UUID accountId, String verificationToken, String registrationBody) {}
}
