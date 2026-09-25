package com.goodthingjar.jar.domain;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class JarLockPolicy {
  public boolean isLocked(Instant trustedNow, Instant effectiveUnlockAt) {
    return trustedNow.isBefore(effectiveUnlockAt);
  }
}
