package com.goodthingjar.platform.observability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_audit_event")
public class SecurityAuditEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @Column(name = "actor_scope", length = 64)
  private String actorScope;

  @Column(nullable = false, length = 32)
  private String outcome;

  @Column(name = "correlation_id", length = 100)
  private String correlationId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected SecurityAuditEventEntity() {}

  public SecurityAuditEventEntity(
      String eventType,
      String actorScope,
      String outcome,
      String correlationId,
      Instant occurredAt) {
    this.eventType = eventType;
    this.actorScope = actorScope;
    this.outcome = outcome;
    this.correlationId = correlationId;
    this.occurredAt = occurredAt;
  }
}
