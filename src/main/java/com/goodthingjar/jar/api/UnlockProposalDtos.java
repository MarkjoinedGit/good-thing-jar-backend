package com.goodthingjar.jar.api;

import com.goodthingjar.jar.persistence.UnlockProposalEntity;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class UnlockProposalDtos {
  private UnlockProposalDtos() {}

  public record CreateUnlockProposalRequest(@NotNull Instant proposedUnlockAt) {}

  public record UnlockProposalResponse(
      UUID id,
      Instant proposedUnlockAt,
      UUID proposedByAccountId,
      UnlockProposalEntity.Status status,
      Instant createdAt) {
    public static UnlockProposalResponse from(UnlockProposalEntity p) {
      return new UnlockProposalResponse(
          p.getId(),
          p.getProposedUnlockAt(),
          p.getProposedByAccountId(),
          p.getStatus(),
          p.getCreatedAt());
    }
  }
}
