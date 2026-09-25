package com.goodthingjar.identity.application;

import com.goodthingjar.identity.persistence.AccountEntity;
import com.goodthingjar.identity.persistence.AccountRepository;
import com.goodthingjar.identity.persistence.AuthSessionEntity;
import com.goodthingjar.identity.persistence.AuthSessionRepository;
import com.goodthingjar.platform.error.ApiException;
import com.goodthingjar.platform.error.ProblemCode;
import com.goodthingjar.platform.security.SecretHasher;
import com.goodthingjar.platform.throttle.AbuseThrottleService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
  public record Tokens(
      String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {}

  private final AccountRepository accounts;
  private final AuthSessionRepository sessions;
  private final PasswordEncoder passwords;
  private final SecretHasher secrets;
  private final AbuseThrottleService throttle;
  private final Clock clock;

  public SessionService(
      AccountRepository a,
      AuthSessionRepository s,
      PasswordEncoder p,
      SecretHasher h,
      AbuseThrottleService t,
      Clock c) {
    accounts = a;
    sessions = s;
    passwords = p;
    secrets = h;
    throttle = t;
    clock = c;
  }

  @Transactional
  public Tokens create(String email, String password, String scope) {
    String normalized = RegistrationService.normalizeEmail(email);
    throttle.check("authentication", scope + ":" + normalized);
    AccountEntity account =
        accounts
            .findByEmailNormalized(normalized)
            .filter(a -> passwords.matches(password, a.getPasswordHash()))
            .orElseThrow(() -> new ApiException(ProblemCode.AUTHENTICATION, "Invalid credentials"));
    if (!account.isVerified())
      throw new ApiException(ProblemCode.EMAIL_NOT_VERIFIED, "Email verification required");
    return issue(account.getId(), clock.instant());
  }

  @Transactional(noRollbackFor = ApiException.class)
  public Tokens refresh(String rawRefresh, String scope) {
    throttle.check("authentication", scope);
    Instant now = clock.instant();
    String hash = secrets.hashToken(rawRefresh);
    AuthSessionEntity session =
        sessions
            .findByRefreshTokenForUpdate(hash)
            .orElseThrow(() -> new ApiException(ProblemCode.AUTHENTICATION, "Invalid session"));
    if (session.isPreviousRefreshHash(hash)) {
      session.revoke(now);
      throw new ApiException(ProblemCode.AUTHENTICATION, "Invalid session");
    }
    if (!session.isCurrentRefreshHash(hash) || !session.refreshUsableAt(now)) {
      session.revoke(now);
      throw new ApiException(ProblemCode.AUTHENTICATION, "Invalid session");
    }
    String access = secrets.randomToken(), refresh = secrets.randomToken();
    session.rotate(secrets.hashToken(access), secrets.hashToken(refresh), now);
    return new Tokens(access, refresh, session.getAccessExpiresAt(), session.getRefreshExpiresAt());
  }

  @Transactional
  public void logout(String rawAccess) {
    sessions
        .findByAccessTokenForUpdate(secrets.hashToken(rawAccess))
        .ifPresent(s -> s.revoke(clock.instant()));
  }

  private Tokens issue(java.util.UUID accountId, Instant now) {
    String access = secrets.randomToken(), refresh = secrets.randomToken();
    AuthSessionEntity entity =
        sessions.save(
            new AuthSessionEntity(
                accountId, secrets.hashToken(access), secrets.hashToken(refresh), now));
    return new Tokens(access, refresh, entity.getAccessExpiresAt(), entity.getRefreshExpiresAt());
  }
}
