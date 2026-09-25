package com.goodthingjar.platform.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class ObservabilityConfiguration {

  @Bean
  MeterBinder operationalDatabaseMetrics(JdbcTemplate jdbc) {
    return registry -> {
      Gauge.builder("goodthingjar.outbox.backlog", jdbc, ObservabilityConfiguration::outboxBacklog)
          .description("Outbox messages waiting for terminal processing")
          .register(registry);
      Gauge.builder(
              "goodthingjar.security.events.total",
              jdbc,
              source -> safeCount(source, "select count(*) from security_audit_event"))
          .description("Persisted sanitized security audit events")
          .register(registry);
    };
  }

  @Bean
  HealthIndicator outboxHealthIndicator(JdbcTemplate jdbc) {
    return () -> {
      double backlog = outboxBacklog(jdbc);
      return Double.isNaN(backlog) ? Health.unknown().build() : Health.up().build();
    };
  }

  @Bean
  FilterRegistrationBean<RequestMetricsFilter> sanitizedRequestMetrics(MeterRegistry registry) {
    var registration = new FilterRegistrationBean<>(new RequestMetricsFilter(registry));
    registration.setName("sanitizedRequestMetrics");
    registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
    return registration;
  }

  private static double outboxBacklog(JdbcTemplate jdbc) {
    return safeCount(
        jdbc, "select count(*) from outbox_message where status in ('PENDING','CLAIMED')");
  }

  private static double safeCount(JdbcTemplate jdbc, String sql) {
    try {
      Long value = jdbc.queryForObject(sql, Long.class);
      return value == null ? 0 : value.doubleValue();
    } catch (RuntimeException unavailable) {
      return Double.NaN;
    }
  }

  static final class RequestMetricsFilter extends OncePerRequestFilter {
    private final MeterRegistry registry;

    RequestMetricsFilter(MeterRegistry registry) {
      this.registry = registry;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      long started = System.nanoTime();
      try {
        chain.doFilter(request, response);
      } finally {
        String outcome = statusFamily(response.getStatus());
        Tags tags = Tags.of("method", request.getMethod(), "outcome", outcome);
        registry
            .timer("goodthingjar.http.requests", tags)
            .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        if (response.getStatus() >= 400) {
          registry.counter("goodthingjar.http.failures", tags).increment();
        }
      }
    }

    private static String statusFamily(int status) {
      if (status >= 100 && status < 600) return (status / 100) + "xx";
      return "unknown";
    }
  }
}
