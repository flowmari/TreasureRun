package com.flowmari.treasurerun.rankingapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmari.treasurerun.rankingapi.RankingApiApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(
    classes = RankingApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class RankingReadApiMySqlIntegrationTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("treasurerun")
          .withUsername("treasurerun")
          .withPassword("treasurerun");

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void configureDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.hikari.read-only", () -> "true");
  }

  @BeforeAll
  static void applyExistingPluginMigrations() throws Exception {
    try (Connection connection = openConnection()) {
      executeMigration(connection, "db/migration/V1__create_ranking_tables.sql");
      executeMigration(connection, "db/migration/V2__support_monthly_seasons.sql");
      executeMigration(connection, "db/migration/V3__create_transactional_outbox.sql");
    }
  }

  @BeforeEach
  void seedLeaderboardRows() throws Exception {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {

      statement.executeUpdate("DELETE FROM outbox_events");
      statement.executeUpdate("DELETE FROM season_scores");
      statement.executeUpdate("DELETE FROM alltime_scores");
      statement.executeUpdate("DELETE FROM seasons");

      statement.executeUpdate("""
          INSERT INTO alltime_scores
            (uuid, name, score, wins, best_time_ms, lang_code)
          VALUES
            ('00000000-0000-0000-0000-000000000001', 'Astra', 300, 2, 15000, 'en'),
            ('00000000-0000-0000-0000-000000000002', 'Blitz', 450, 3, 11000, 'de'),
            ('00000000-0000-0000-0000-000000000003', 'Comet', 300, 4, 12000, 'ja')
          """);
    }
  }

  @Test
  void returnsOrderedAllTimeLeaderboardThroughRealHttpAndMySql() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/rankings/all-time?limit=2", String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals(2, body.size());

    assertEquals("Blitz", body.get(0).get("playerName").asText());
    assertEquals(450, body.get(0).get("score").asInt());
    assertEquals("de", body.get(0).get("languageCode").asText());

    assertEquals("Comet", body.get(1).get("playerName").asText());
    assertEquals(300, body.get(1).get("score").asInt());

    assertFalse(response.getBody().contains("uuid"),
        "The public leaderboard DTO must not expose player UUIDs.");

    assertEquals(3, intQuery("SELECT COUNT(*) FROM alltime_scores"));
    assertEquals(0, intQuery("SELECT COUNT(*) FROM outbox_events"));
  }

  @Test
  void rejectsLimitOutsidePublicApiBoundary() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/rankings/all-time?limit=101", String.class);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
  }

  private static Connection openConnection() throws Exception {
    return DriverManager.getConnection(
        MYSQL.getJdbcUrl(),
        MYSQL.getUsername(),
        MYSQL.getPassword()
    );
  }

  private static int intQuery(String sql) throws Exception {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(sql)) {

      assertTrue(resultSet.next(), "Expected one result row for SQL: " + sql);
      return resultSet.getInt(1);
    }
  }

  private static void executeMigration(Connection connection, String resourcePath) throws Exception {
    String sql;

    try (InputStream stream = RankingReadApiMySqlIntegrationTest.class
        .getClassLoader()
        .getResourceAsStream(resourcePath)) {

      sql = new String(
          Objects.requireNonNull(stream, "Missing migration resource: " + resourcePath)
              .readAllBytes(),
          StandardCharsets.UTF_8
      );
    }

    String executableSql = Arrays.stream(sql.split("\\R"))
        .filter(line -> !line.trim().startsWith("--"))
        .collect(Collectors.joining("\n"));

    for (String statementSql : executableSql.split(";")) {
      if (!statementSql.isBlank()) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(statementSql.trim());
        }
      }
    }
  }
}
