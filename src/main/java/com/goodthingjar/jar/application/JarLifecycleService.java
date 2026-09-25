package com.goodthingjar.jar.application;

import com.goodthingjar.jar.domain.*;
import com.goodthingjar.jar.persistence.*;
import com.goodthingjar.pairing.application.PairAccessService;
import com.goodthingjar.platform.error.*;
import java.time.*;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JarLifecycleService {
  private final JarRepository jars;
  private final PairAccessService pairs;
  private final DefaultUnlockCalculator defaults;
  private final JarLockPolicy locks;
  private final Clock clock;

  public JarLifecycleService(
      JarRepository j, PairAccessService p, DefaultUnlockCalculator d, JarLockPolicy l, Clock c) {
    jars = j;
    pairs = p;
    defaults = d;
    locks = l;
    clock = c;
  }

  @Transactional
  public JarEntity createInitial(UUID pairId, String zone, Instant createdAt) {
    return jars.save(
        new JarEntity(pairId, 1, zone, defaults.calculate(createdAt, zone), createdAt));
  }

  @Transactional
  public JarEntity createNext(UUID accountId) {
    var pair = pairs.requireCurrent(accountId);
    pairs.lock(pair.id());
    Instant now = clock.instant();
    JarEntity current =
        jars.findCurrentForUpdate(pair.id()).orElseThrow(ProtectedResourceErrors::notFound);
    if (locks.isLocked(now, current.getEffectiveUnlockAt()))
      throw new ApiException(ProblemCode.CONFLICT, "Current jar is still locked");
    current.closeCycle();
    jars.saveAndFlush(current);
    try {
      return jars.saveAndFlush(
          new JarEntity(
              pair.id(),
              current.getSequenceNumber() + 1,
              pair.timeZone(),
              defaults.calculate(now, pair.timeZone()),
              now));
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ProblemCode.CONFLICT, "A current jar already exists");
    }
  }
}
