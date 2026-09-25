package com.goodthingjar.pairing.api;

import java.time.Instant;
import java.util.UUID;

public record PairCreated(UUID pairId, String timeZone, Instant createdAt) {}
