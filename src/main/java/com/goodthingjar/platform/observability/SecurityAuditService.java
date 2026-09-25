package com.goodthingjar.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {
  private final SecurityAuditRepository repository;
  private final Clock clock;
  private final MeterRegistry meters;

  public SecurityAuditService(
      SecurityAuditRepository repository, Clock clock, MeterRegistry meters) {
    this.repository = repository;
    this.clock = clock;
    this.meters = meters;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String eventType, String actorScope, String outcome, String correlationId) {
    repository.save(
        new SecurityAuditEventEntity(
            eventType, actorScope, outcome, correlationId, clock.instant()));
    meters
        .counter("goodthingjar.security.events", "event", eventType, "outcome", outcome)
        .increment();
  }
}
