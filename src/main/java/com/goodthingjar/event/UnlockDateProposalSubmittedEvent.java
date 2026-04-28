package com.goodthingjar.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UnlockDateProposalSubmittedEvent(
    UUID proposalId,
    UUID jarId,
    UUID proposedBy,
    OffsetDateTime newUnlocksAt,
    OffsetDateTime occurredAt
) {
}

