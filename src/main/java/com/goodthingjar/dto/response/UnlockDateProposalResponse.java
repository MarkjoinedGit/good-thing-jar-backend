package com.goodthingjar.dto.response;

import com.goodthingjar.entity.enums.UnlockDateProposalStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UnlockDateProposalResponse(
    UUID proposalId,
    UUID jarId,
    UUID proposedBy,
    UUID approvedBy,
    OffsetDateTime newUnlocksAt,
    UnlockDateProposalStatus status,
    OffsetDateTime expiresAt,
    String rejectionReason,
    OffsetDateTime createdAt,
    OffsetDateTime respondedAt
) {
}

