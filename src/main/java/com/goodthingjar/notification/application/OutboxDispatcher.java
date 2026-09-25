package com.goodthingjar.notification.application;

import com.goodthingjar.notification.infrastructure.SmtpMailGateway;
import com.goodthingjar.notification.persistence.OutboxMessageEntity;
import com.goodthingjar.notification.persistence.OutboxMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "gtj.outbox.scheduling-enabled", matchIfMissing = true)
public class OutboxDispatcher {

  private final OutboxMessageRepository outbox;
  private final SmtpMailGateway mail;
  private final DeliverySecretCipher cipher;
  private final ObjectProvider<InvitationDeliveryPort> invitations;
  private final Clock clock;
  private final TransactionTemplate transactions;
  private final Duration claimLease;

  public OutboxDispatcher(
      OutboxMessageRepository o,
      SmtpMailGateway m,
      DeliverySecretCipher c,
      ObjectProvider<InvitationDeliveryPort> i,
      Clock clock,
      TransactionTemplate transactions,
      @Value("${gtj.outbox.claim-lease:PT5M}") Duration claimLease) {
    outbox = o;
    mail = m;
    cipher = c;
    invitations = i;
    this.clock = clock;
    this.transactions = transactions;
    this.claimLease = claimLease;
  }

  @Scheduled(fixedDelayString = "${gtj.outbox.poll-delay:PT1S}")
  public void dispatch() {
    List<UUID> batch = claimBatch();
    for (UUID messageId : batch) {
      OutboxMessageEntity message = outbox.findById(messageId).orElse(null);
      if (message == null || message.getStatus() != OutboxMessageEntity.Status.CLAIMED) continue;
      try {
        if ("EMAIL_VERIFICATION".equals(message.getMessageType()))
          mail.sendVerification(
              message.getRecipient(), cipher.decrypt(message.getEncryptedSecret()));
        else if ("INVITATION".equals(message.getMessageType())) {
          InvitationDeliveryPort port = invitations.getIfAvailable();
          if (port == null || !port.mayDeliver(message.getAggregateId())) {
            complete(messageId, DeliveryOutcome.SKIPPED);
            continue;
          }
          mail.sendInvitation(message.getRecipient(), message.getPayload());
        } else {
          complete(messageId, DeliveryOutcome.SKIPPED);
          continue;
        }
        complete(messageId, DeliveryOutcome.DELIVERED);
      } catch (RuntimeException failure) {
        complete(messageId, DeliveryOutcome.FAILED);
      }
    }
  }

  private List<UUID> claimBatch() {
    List<UUID> claimed =
        transactions.execute(
            status -> {
              var now = clock.instant();
              List<OutboxMessageEntity> rows =
                  outbox.claimable(now, now.minus(claimLease), PageRequest.of(0, 25));
              rows.forEach(row -> row.claim(now));
              return rows.stream().map(OutboxMessageEntity::getId).toList();
            });
    return claimed == null ? List.of() : claimed;
  }

  private void complete(UUID messageId, DeliveryOutcome outcome) {
    transactions.executeWithoutResult(
        status -> {
          OutboxMessageEntity message = outbox.findByIdForUpdate(messageId).orElse(null);
          if (message == null || message.getStatus() != OutboxMessageEntity.Status.CLAIMED) return;
          var now = clock.instant();
          if ("INVITATION".equals(message.getMessageType()) && outcome != DeliveryOutcome.SKIPPED) {
            InvitationDeliveryPort port = invitations.getIfAvailable();
            if (port != null) {
              port.recordResult(message.getAggregateId(), outcome == DeliveryOutcome.DELIVERED);
            }
          }
          switch (outcome) {
            case DELIVERED -> message.delivered(now);
            case FAILED -> message.failed(now, "delivery_failed");
            case SKIPPED -> message.skipped(now);
          }
        });
  }

  private enum DeliveryOutcome {
    DELIVERED,
    FAILED,
    SKIPPED
  }
}
