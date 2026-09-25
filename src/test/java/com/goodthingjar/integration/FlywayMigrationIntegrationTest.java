package com.goodthingjar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "gtj.outbox.scheduling-enabled=false")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired Flyway flyway;
  @Autowired JdbcTemplate jdbc;

  @Test
  void cleanPostgresIsMigratedAndValidatedByHibernate() {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("005");
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

    List<String> tables =
        jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema='public'",
            String.class);
    assertThat(tables)
        .contains(
            "account",
            "email_verification",
            "auth_session",
            "outbox_message",
            "security_audit_event",
            "abuse_throttle_bucket",
            "invitation",
            "couple_pair",
            "pair_member",
            "shared_jar",
            "jar_entry",
            "unlock_proposal");
  }
}
