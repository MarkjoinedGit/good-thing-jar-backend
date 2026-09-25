package com.goodthingjar.jar.application;

import com.goodthingjar.jar.domain.JarLockPolicy;
import com.goodthingjar.jar.persistence.*;
import com.goodthingjar.pairing.application.PairAccessService;
import com.goodthingjar.platform.error.*;
import com.goodthingjar.platform.throttle.AbuseThrottleService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntryCommandService {
  private final JarRepository jars;
  private final EntryRepository entries;
  private final PairAccessService pairs;
  private final JarLockPolicy locks;
  private final AbuseThrottleService throttle;
  private final Clock clock;

  public EntryCommandService(
      JarRepository j,
      EntryRepository e,
      PairAccessService p,
      JarLockPolicy l,
      AbuseThrottleService t,
      Clock c) {
    jars = j;
    entries = e;
    pairs = p;
    locks = l;
    throttle = t;
    clock = c;
  }

  @Transactional
  public void add(UUID account, UUID jarId, String text) {
    throttle.check("entry-write", account.toString());
    if (text == null || text.length() < 1 || text.length() > 5000)
      throw new ApiException(
          ProblemCode.VALIDATION, "Entry text must contain 1 to 5000 characters");
    JarEntity jar = jars.findByIdForShare(jarId).orElseThrow(ProtectedResourceErrors::notFound);
    pairs.requireMember(jar.getPairId(), account);
    var now = clock.instant();
    if (!jar.isCurrent() || !locks.isLocked(now, jar.getEffectiveUnlockAt()))
      throw new ApiException(ProblemCode.CONFLICT, "Jar is read-only");
    entries.save(new EntryEntity(jarId, account, text, now));
  }
}
