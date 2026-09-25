package com.goodthingjar.platform.time;

import com.goodthingjar.platform.error.ApiException;
import com.goodthingjar.platform.error.ProblemCode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class TimeZoneRules {
  public ZoneId requireIanaZone(String value) {
    if (value == null || value.isBlank()) {
      throw new ApiException(ProblemCode.VALIDATION, "A time zone is required");
    }
    try {
      ZoneId zone = ZoneId.of(value);
      if (zone.getId().equals("Z")
          || zone.getId().startsWith("+")
          || zone.getId().startsWith("-")) {
        throw new DateTimeException("Fixed offsets are not IANA region identifiers");
      }
      return zone;
    } catch (DateTimeException exception) {
      throw new ApiException(ProblemCode.VALIDATION, "Invalid IANA time zone");
    }
  }

  public Instant toInstant(LocalDateTime localDateTime, String zoneId) {
    return localDateTime.atZone(requireIanaZone(zoneId)).toInstant();
  }
}
