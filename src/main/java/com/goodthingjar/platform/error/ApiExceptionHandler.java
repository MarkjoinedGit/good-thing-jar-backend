package com.goodthingjar.platform.error;

import com.goodthingjar.platform.observability.SecurityAuditService;
import com.goodthingjar.platform.security.AuthenticatedAccount;
import com.goodthingjar.platform.security.SecretHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private final SecurityAuditService audit;
  private final SecretHasher hasher;

  public ApiExceptionHandler(SecurityAuditService audit, SecretHasher hasher) {
    this.audit = audit;
    this.hasher = hasher;
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException exception, HttpServletRequest request) {
    ProblemCode code = exception.problemCode();
    String correlationId = correlationId(request);
    ProblemDetail body = problem(code, exception.getMessage(), request, correlationId);
    if (code == ProblemCode.PROTECTED_NOT_FOUND || code == ProblemCode.LOCKED) {
      recordProtectedDenial(request, code, correlationId);
    }
    HttpHeaders headers = new HttpHeaders();
    if (exception instanceof ThrottledException throttled) {
      headers.set(HttpHeaders.RETRY_AFTER, Long.toString(throttled.retryAfterSeconds()));
    }
    return new ResponseEntity<>(body, headers, code.status());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> validation(Exception exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            problem(
                ProblemCode.VALIDATION,
                "Request validation failed",
                request,
                correlationId(request)));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
    ProblemDetail body = ProblemDetail.forStatus(500);
    body.setTitle("Unexpected failure");
    body.setDetail("The request could not be completed");
    body.setType(URI.create("urn:good-thing-jar:problem:unexpected"));
    body.setProperty("code", "unexpected_failure");
    body.setProperty("correlationId", correlationId(request));
    return ResponseEntity.internalServerError().body(body);
  }

  private ProblemDetail problem(
      ProblemCode code, String detail, HttpServletRequest request, String correlationId) {
    ProblemDetail body = ProblemDetail.forStatus(code.status());
    body.setTitle(code.title());
    body.setDetail(detail);
    body.setType(URI.create("urn:good-thing-jar:problem:" + code.code()));
    body.setProperty("code", code.code());
    body.setProperty("correlationId", correlationId);
    return body;
  }

  private void recordProtectedDenial(
      HttpServletRequest request, ProblemCode code, String correlationId) {
    if (request.getUserPrincipal() instanceof org.springframework.security.core.Authentication auth
        && auth.getPrincipal() instanceof AuthenticatedAccount actor) {
      try {
        audit.record(
            "protected_resource_denial",
            hasher.pseudonymousScope(actor.accountId().toString()),
            code.code(),
            correlationId);
      } catch (RuntimeException ignored) {
        // Audit persistence must never alter the privacy-safe external response.
      }
    }
  }

  private String correlationId(HttpServletRequest request) {
    Object existing = request.getAttribute("correlationId");
    return existing == null ? UUID.randomUUID().toString() : existing.toString();
  }
}
