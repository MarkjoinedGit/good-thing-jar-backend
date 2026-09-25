package com.goodthingjar.pairing.api;

import java.time.Instant;
import java.util.*;

public record PairResponse(
    UUID id, String timeZone, List<UUID> memberAccountIds, Instant createdAt) {
  public PairResponse {
    memberAccountIds = List.copyOf(memberAccountIds);
  }

  @Override
  public List<UUID> memberAccountIds() {
    return List.copyOf(memberAccountIds);
  }
}
