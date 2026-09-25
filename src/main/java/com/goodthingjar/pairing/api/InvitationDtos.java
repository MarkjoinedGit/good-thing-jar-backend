package com.goodthingjar.pairing.api;

import com.goodthingjar.pairing.domain.InvitationStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class InvitationDtos {
  private InvitationDtos() {}

  public record CreateInvitationRequest(
      @NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 64) String timeZone) {}

  public record InvitationSummary(
      UUID id,
      String direction,
      String counterpartEmail,
      InvitationStatus status,
      String timeZone,
      Instant expiresAt,
      Instant createdAt) {}
}
