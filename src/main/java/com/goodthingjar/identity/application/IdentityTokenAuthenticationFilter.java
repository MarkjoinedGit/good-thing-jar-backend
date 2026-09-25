package com.goodthingjar.identity.application;

import com.goodthingjar.identity.persistence.AuthSessionRepository;
import com.goodthingjar.platform.security.AuthenticatedAccount;
import com.goodthingjar.platform.security.OpaqueTokenAuthenticationFilter;
import com.goodthingjar.platform.security.SecretHasher;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class IdentityTokenAuthenticationFilter extends OpaqueTokenAuthenticationFilter {
  private final AuthSessionRepository sessions;
  private final SecretHasher hasher;
  private final Clock clock;

  public IdentityTokenAuthenticationFilter(
      AuthSessionRepository sessions, SecretHasher hasher, Clock clock) {
    this.sessions = sessions;
    this.hasher = hasher;
    this.clock = clock;
  }

  @Override
  protected Optional<AuthenticatedAccount> authenticateToken(String rawToken) {
    return sessions
        .findByAccessTokenHash(hasher.hashToken(rawToken))
        .filter(s -> s.accessUsableAt(clock.instant()))
        .map(s -> new AuthenticatedAccount(s.getAccountId(), s.getId()));
  }
}
