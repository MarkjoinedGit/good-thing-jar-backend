package com.goodthingjar.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthenticationDtos {
  private AuthenticationDtos() {}

  public record RegisterRequest(
      @NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Size(min = 12, max = 128) String password) {}

  public record RegistrationResponse(UUID accountId, boolean verificationRequired) {}

  public record VerifyEmailRequest(@NotBlank @Size(min = 32, max = 512) String token) {}

  public record ResendRequest(@NotBlank @Email @Size(max = 320) String email) {}

  public record ResendResponse(boolean accepted) {}

  public record CreateSessionRequest(
      @NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 128) String password) {}

  public record RefreshSessionRequest(@NotBlank @Size(min = 32, max = 512) String refreshToken) {}

  public record SessionResponse(
      String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {}
}
