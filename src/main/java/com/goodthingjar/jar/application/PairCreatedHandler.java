package com.goodthingjar.jar.application;

import com.goodthingjar.pairing.api.PairCreated;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class PairCreatedHandler {
  private final JarLifecycleService jars;

  public PairCreatedHandler(JarLifecycleService j) {
    jars = j;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(PairCreated event) {
    jars.createInitial(event.pairId(), event.timeZone(), event.createdAt());
  }
}
