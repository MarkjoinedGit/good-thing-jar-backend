package com.goodthingjar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayUpgradeIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

  @Test
  void upgradesAnExistingSchemaWithoutChangingOwnedData() throws Exception {
    Flyway baseline = flyway("003");
    baseline.migrate();
    UUID accountId = UUID.randomUUID();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement =
            connection.prepareStatement(
                "insert into account(id,email_normalized,password_hash,verified_at,created_at,version) values (?,?,?,?,?,0)")) {
      statement.setObject(1, accountId);
      statement.setString(2, "upgrade-preserved@example.test");
      statement.setString(3, "existing-password-hash");
      statement.setTimestamp(4, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
      statement.setTimestamp(5, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
      statement.executeUpdate();
    }

    Flyway latest = flyway(null);
    latest.migrate();

    assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("005");
    assertThat(latest.validateWithResult().validationSuccessful).isTrue();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var account =
            connection.prepareStatement("select email_normalized from account where id=?");
        var newTables =
            connection.prepareStatement(
                "select count(*) from information_schema.tables where table_schema='public' and table_name in ('shared_jar','jar_entry','unlock_proposal')")) {
      account.setObject(1, accountId);
      try (var result = account.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("upgrade-preserved@example.test");
      }
      try (var result = newTables.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(3);
      }
    }
  }

  @Test
  void hibernateSchemaGenerationIsDisabled() throws IOException {
    Properties properties = new Properties();
    try (InputStream resource =
        getClass().getClassLoader().getResourceAsStream("application.properties")) {
      assertThat(resource).isNotNull();
      properties.load(resource);
    }
    assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
  }

  private Flyway flyway(String target) {
    var configuration =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration");
    if (target != null) configuration.target(target);
    return configuration.load();
  }
}
