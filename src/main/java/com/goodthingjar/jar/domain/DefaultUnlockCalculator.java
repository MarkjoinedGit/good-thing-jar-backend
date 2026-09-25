package com.goodthingjar.jar.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class DefaultUnlockCalculator {
  public Instant calculate(Instant createdAt, String fixedZone) {
    ZoneId zone = ZoneId.of(fixedZone);
    int creationYear = createdAt.atZone(zone).getYear();
    Instant result = LocalDate.of(creationYear + 1, 1, 1).atStartOfDay(zone).toInstant();
    if (!result.isAfter(createdAt))
      throw new IllegalStateException("Default unlock must be after creation");
    return result;
  }
}
