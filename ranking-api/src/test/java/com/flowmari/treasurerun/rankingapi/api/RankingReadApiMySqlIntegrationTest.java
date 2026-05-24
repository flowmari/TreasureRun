package com.flowmari.treasurerun.rankingapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmari.treasurerun.rankingapi.RankingApiApplication;
import org.flywaydb.core.Flyway;
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

  @Autowired
  private Flyway flyway;

  @DynamicPropertySource
  static void configureDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);

    /*
     * Integration tests require schema migration and seed-data writes.
     * Production API endpoints remain read-oriented; this mutable test connection
     * is used only to construct and verify the disposable test database.
     */
    registry.add("spring.datasource.hikari.read-only", () -> "false");

    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.flyway.validate-on-migrate", () -> "true");
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
  void appliesPluginOwnedSchemaThroughFlywayBeforeServingReads() throws Exception {
    List<String> appliedVersions = Arrays.stream(flyway.info().applied())
        .map(info -> info.getVersion().getVersion())
        .toList();

    assertEquals(List.of("1", "2", "3"), appliedVersions);
    assertEquals(3, intQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
    assertEquals(3, intQuery("""
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN ('alltime_scores', 'seasons', 'outbox_events')
        """));
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

  @Test
  void publishesOpenApiContractForLeaderboardBoundary() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/v3/api-docs", String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());

    JsonNode document = objectMapper.readTree(response.getBody());

    assertTrue(document.get("openapi").asText().startsWith("3."));
    assertEquals("TreasureRun Ranking Read API",
        document.at("/info/title").asText());
    assertEquals("v1", document.at("/info/version").asText());

    JsonNode operation =
        document.at("/paths/~1api~1v1~1rankings~1all-time/get");

    assertFalse(operation.isMissingNode(), "Expected GET /api/v1/rankings/all-time in OpenAPI.");
    assertEquals("List all-time leaderboard entries",
        operation.get("summary").asText());
    assertTrue(operation.path("responses").has("200"));
    assertTrue(operation.path("responses").has("400"));

    JsonNode limitParameter = null;
    for (JsonNode parameter : operation.path("parameters")) {
      if ("limit".equals(parameter.path("name").asText())) {
        limitParameter = parameter;
        break;
      }
    }

    assertNotNull(limitParameter, "Expected documented limit query parameter.");
    assertEquals("query", limitParameter.path("in").asText());
    assertEquals(10, limitParameter.path("schema").path("default").asInt());
    assertEquals(1, limitParameter.path("schema").path("minimum").asInt());
    assertEquals(100, limitParameter.path("schema").path("maximum").asInt());

    JsonNode publicDto =
        document.at("/components/schemas/LeaderboardEntryResponse/properties");

    assertTrue(publicDto.has("playerName"));
    assertTrue(publicDto.has("score"));
    assertTrue(publicDto.has("wins"));
    assertTrue(publicDto.has("bestTimeMs"));
    assertTrue(publicDto.has("languageCode"));
    assertFalse(publicDto.has("uuid"),
        "OpenAPI public response schema must not expose UUID values.");
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
}
