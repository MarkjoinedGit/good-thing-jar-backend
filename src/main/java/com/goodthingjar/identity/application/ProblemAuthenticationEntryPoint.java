package com.goodthingjar.identity.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"type\":\"urn:good-thing-jar:problem:authentication_required\",\"title\":\"Authentication required\",\"status\":401,\"detail\":\"Authentication is required\",\"code\":\"authentication_required\",\"correlationId\":\"%s\"}"
                .formatted(correlationId(request)));
  }

  private static String correlationId(HttpServletRequest request) {
    Object existing = request.getAttribute("correlationId");
    return existing == null ? UUID.randomUUID().toString() : existing.toString();
  }
}
