package com.goodthingjar.identity.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class IdentitySecurityConfiguration {
  @Bean
  SecurityFilterChain applicationSecurity(
      HttpSecurity http,
      IdentityTokenAuthenticationFilter filter,
      ProblemAuthenticationEntryPoint authenticationEntryPoint)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(errors -> errors.authenticationEntryPoint(authenticationEntryPoint))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/auth/registrations",
                        "/auth/email-verifications",
                        "/auth/email-verification-resends",
                        "/auth/sessions",
                        "/auth/sessions/refresh",
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
