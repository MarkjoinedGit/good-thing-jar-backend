package com.goodthingjar.identity.api;

import com.goodthingjar.identity.application.EmailVerificationService;
import com.goodthingjar.identity.application.RegistrationService;
import com.goodthingjar.identity.application.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {
  private final RegistrationService registrations;
  private final EmailVerificationService verifications;
  private final SessionService sessions;

  public AuthenticationController(
      RegistrationService r, EmailVerificationService v, SessionService s) {
    registrations = r;
    verifications = v;
    sessions = s;
  }

  @PostMapping("/registrations")
  ResponseEntity<AuthenticationDtos.RegistrationResponse> register(
      @Valid @RequestBody AuthenticationDtos.RegisterRequest request) {
    return ResponseEntity.accepted()
        .body(
            new AuthenticationDtos.RegistrationResponse(
                registrations.register(request.email(), request.password()), true));
  }

  @PostMapping("/email-verifications")
  ResponseEntity<Void> verify(@Valid @RequestBody AuthenticationDtos.VerifyEmailRequest request) {
    verifications.verify(request.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/email-verification-resends")
  ResponseEntity<AuthenticationDtos.ResendResponse> resend(
      @Valid @RequestBody AuthenticationDtos.ResendRequest request, HttpServletRequest servlet) {
    verifications.resend(request.email(), remote(servlet));
    return ResponseEntity.accepted().body(new AuthenticationDtos.ResendResponse(true));
  }

  @PostMapping("/sessions")
  AuthenticationDtos.SessionResponse create(
      @Valid @RequestBody AuthenticationDtos.CreateSessionRequest request,
      HttpServletRequest servlet) {
    return response(sessions.create(request.email(), request.password(), remote(servlet)));
  }

  @PostMapping("/sessions/refresh")
  AuthenticationDtos.SessionResponse refresh(
      @Valid @RequestBody AuthenticationDtos.RefreshSessionRequest request,
      HttpServletRequest servlet) {
    return response(sessions.refresh(request.refreshToken(), remote(servlet)));
  }

  @DeleteMapping("/sessions/current")
  ResponseEntity<Void> logout(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) sessions.logout(header.substring(7));
    return ResponseEntity.noContent().build();
  }

  private static String remote(HttpServletRequest r) {
    return r.getRemoteAddr() == null ? "unknown" : r.getRemoteAddr();
  }

  private static AuthenticationDtos.SessionResponse response(SessionService.Tokens t) {
    return new AuthenticationDtos.SessionResponse(
        t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt());
  }
}
