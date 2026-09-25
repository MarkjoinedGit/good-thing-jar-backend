package com.goodthingjar.jar.api;

import java.time.Instant;
import java.util.*;

public final class JarDtos {
  private JarDtos() {}

  public record JarSummary(
      UUID id,
      int sequenceNumber,
      boolean current,
      String timeZone,
      Instant effectiveUnlockAt,
      String lockStatus,
      Instant createdAt) {}

  public record JarDetail(
      UUID id,
      int sequenceNumber,
      boolean current,
      String timeZone,
      Instant effectiveUnlockAt,
      String lockStatus,
      Instant createdAt,
      Object pendingUnlockProposal) {}

  public record CreateEntryRequest(
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 5000)
          String text) {}

  public record EntryResponse(UUID id, String text, UUID authorAccountId, Instant createdAt) {}

  public record EntryPage(List<EntryResponse> items, String nextCursor, boolean hasMore) {
    public EntryPage {
      items = List.copyOf(items);
    }

    @Override
    public List<EntryResponse> items() {
      return List.copyOf(items);
    }
  }
}
