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
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
  private final AccountRepository accounts;
  private final EmailVerificationRepository verifications;
  private final OutboxMessageRepository outbox;
  private final PasswordEncoder passwords;
  private final SecretHasher secrets;
  private final DeliverySecretCipher cipher;
  private final Clock clock;

  public RegistrationService(
      AccountRepository a,
      EmailVerificationRepository v,
      OutboxMessageRepository o,
      PasswordEncoder p,
      SecretHasher s,
      DeliverySecretCipher c,
      Clock clock) {
    accounts = a;
    verifications = v;
    outbox = o;
    passwords = p;
    secrets = s;
    cipher = c;
    this.clock = clock;
  }

  public static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional
  public UUID register(String email, String password) {
    if (password == null || password.length() < 12 || password.length() > 128)
      throw new ApiException(ProblemCode.VALIDATION, "Password must contain 12 to 128 characters");
    String normalized = normalizeEmail(email);
    Instant now = clock.instant();
    try {
      AccountEntity account =
          accounts.saveAndFlush(new AccountEntity(normalized, passwords.encode(password), now));
      String token = secrets.randomToken();
      verifications.save(
          new EmailVerificationEntity(account.getId(), secrets.hashToken(token), now));
      outbox.save(
          new OutboxMessageEntity(
              "EMAIL_VERIFICATION", account.getId(), normalized, "{}", cipher.encrypt(token), now));
      return account.getId();
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ProblemCode.CONFLICT, "Registration could not be completed");
    }
  }
}
