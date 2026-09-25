package com.goodthingjar.support;

import com.goodthingjar.identity.application.EmailVerificationService;
import com.goodthingjar.identity.application.RegistrationService;
import com.goodthingjar.identity.application.SessionService;
import com.goodthingjar.jar.persistence.JarRepository;
import com.goodthingjar.notification.application.DeliverySecretCipher;
import com.goodthingjar.pairing.application.InvitationDeliveryResultHandler;
import com.goodthingjar.pairing.application.InvitationService;
import com.goodthingjar.pairing.application.PairingService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "gtj.outbox.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresIntegrationSupport.ClockConfiguration.class)
public abstract class PostgresIntegrationSupport {
  protected static final String PASSWORD = "correct horse battery staple";

  static final PostgreSQLContainer<?> POSTGRES;

  static {
    POSTGRES = new PostgreSQLContainer<>("postgres:18");
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected MockMvc mvc;
  @Autowired protected MutableClock clock;
  @Autowired protected RegistrationService registrations;
  @Autowired protected EmailVerificationService verifications;
  @Autowired protected SessionService sessions;
  @Autowired protected DeliverySecretCipher cipher;
  @Autowired protected InvitationService invitations;
  @Autowired protected InvitationDeliveryResultHandler invitationDeliveries;
  @Autowired protected PairingService pairing;
  @Autowired protected JarRepository jars;

  @BeforeEach
  void resetDatabase() {
    clock.set(java.time.Instant.parse("2026-06-01T12:00:00Z"));
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
    jdbc.update("delete from security_audit_event");
    jdbc.update("delete from account");
  }

  protected User user(String email) {
    UUID accountId = registrations.register(email, PASSWORD);
    String encrypted =
        jdbc.queryForObject(
            "select encrypted_secret from outbox_message where aggregate_id=? and message_type='EMAIL_VERIFICATION'",
            String.class,
            accountId);
    verifications.verify(cipher.decrypt(encrypted));
    String access = sessions.create(email, PASSWORD, "test:" + accountId).accessToken();
    return new User(accountId, email, access);
  }

  protected static String bearer(User user) {
    return "Bearer " + user.accessToken();
  }

  protected User reauthenticate(User user) {
    String access =
        sessions.create(user.email(), PASSWORD, "reauth:" + user.accountId()).accessToken();
    return new User(user.accountId(), user.email(), access);
  }

  protected PairFixture pair(User inviter, User invitee, String timeZone) {
    var invitation = invitations.create(inviter.accountId(), invitee.email(), timeZone);
    invitationDeliveries.recordResult(invitation.getId(), true);
    var pair = pairing.accept(invitee.accountId(), invitation.getId());
    var jar =
        jars.findByPairIdOrderBySequenceNumberDesc(pair.getId()).stream()
            .filter(com.goodthingjar.jar.persistence.JarEntity::isCurrent)
            .findFirst()
            .orElseThrow();
    return new PairFixture(pair.getId(), invitation.getId(), jar.getId());
  }

  protected record User(UUID accountId, String email, String accessToken) {}

  protected record PairFixture(UUID pairId, UUID invitationId, UUID jarId) {}

  @TestConfiguration
  static class ClockConfiguration {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }
}
