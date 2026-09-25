package com.goodthingjar.identity.application;

import com.goodthingjar.identity.persistence.AccountEntity;
import com.goodthingjar.identity.persistence.AccountRepository;
import com.goodthingjar.identity.persistence.EmailVerificationEntity;
import com.goodthingjar.identity.persistence.EmailVerificationRepository;
import com.goodthingjar.notification.application.DeliverySecretCipher;
import com.goodthingjar.notification.persistence.OutboxMessageEntity;
import com.goodthingjar.notification.persistence.OutboxMessageRepository;
import com.goodthingjar.platform.error.ApiException;
import com.goodthingjar.platform.error.ProblemCode;
import com.goodthingjar.platform.security.SecretHasher;
import com.goodthingjar.platform.throttle.AbuseThrottleService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
  private final AccountRepository accounts;
  private final EmailVerificationRepository verifications;
  private final OutboxMessageRepository outbox;
  private final SecretHasher secrets;
  private final DeliverySecretCipher cipher;
  private final AbuseThrottleService throttle;
  private final Clock clock;

  public EmailVerificationService(
      AccountRepository a,
      EmailVerificationRepository v,
      OutboxMessageRepository o,
      SecretHasher s,
      DeliverySecretCipher c,
      AbuseThrottleService t,
      Clock clock) {
    accounts = a;
    verifications = v;
    outbox = o;
    secrets = s;
    cipher = c;
    throttle = t;
    this.clock = clock;
  }

  @Transactional
  public void verify(String rawToken) {
    Instant now = clock.instant();
    String tokenHash = secrets.hashToken(rawToken);
    EmailVerificationEntity candidate =
        verifications
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new ApiException(
                        ProblemCode.VALIDATION, "Verification token is invalid or expired"));
    AccountEntity account = accounts.findByIdForUpdate(candidate.getAccountId()).orElseThrow();
    EmailVerificationEntity verification =
        verifications
            .findByTokenHashForUpdate(tokenHash)
            .orElseThrow(
                () ->
                    new ApiException(
                        ProblemCode.VALIDATION, "Verification token is invalid or expired"));
    if (!verification.usableAt(now))
      throw new ApiException(ProblemCode.VALIDATION, "Verification token is invalid or expired");
    account.verify(now);
    verification.consume(now);
  }

  @Transactional
  public void resend(String email, String throttleScope) {
    String normalized = RegistrationService.normalizeEmail(email);
    throttle.check("verification-resend", throttleScope + ":" + normalized);
    Instant now = clock.instant();
    accounts
        .findByEmailForUpdate(normalized)
        .ifPresent(
            account -> {
              if (account.isVerified()) return;
              verifications.supersedeAll(account.getId(), now);
              String token = secrets.randomToken();
              verifications.save(
                  new EmailVerificationEntity(account.getId(), secrets.hashToken(token), now));
              outbox.save(
                  new OutboxMessageEntity(
                      "EMAIL_VERIFICATION",
                      account.getId(),
                      normalized,
                      "{}",
                      cipher.encrypt(token),
                      now));
            });
  }
}
