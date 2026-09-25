package com.goodthingjar.jar.application;

import com.goodthingjar.platform.error.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EntryCursorCodec {
  public record Cursor(Instant createdAt, UUID id) {}

  public String encode(Instant at, UUID id) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((at.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
  }

  public Cursor decode(String value) {
    try {
      String[] p =
          new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", 2);
      return new Cursor(Instant.parse(p[0]), UUID.fromString(p[1]));
    } catch (Exception e) {
      throw new ApiException(ProblemCode.VALIDATION, "Invalid cursor");
    }
  }
}
