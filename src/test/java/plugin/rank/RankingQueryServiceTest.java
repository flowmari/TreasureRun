package plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RankingQueryServiceTest {

  @Test
  void weeklyPreservesExistingSqlAndMapsRowsWithoutPresentationDefaults() throws Exception {
    AtomicReference<String> capturedSql = new AtomicReference<>();

    Connection connection = connectionReturning(
        capturedSql,
        List.of(
            new Row("Blitz", 450, 11L, "Hard", "de"),
            new Row(null, 300, 12L, null, null)
        )
    );

    List<RankingQueryService.RankingEntry> entries =
        RankingQueryService.loadWeekly(connection);

    assertEquals(
        "SELECT player_name, score, time, difficulty, lang_code, played_at " +
            "FROM scores " +
            "WHERE played_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT 10",
        capturedSql.get()
    );

    assertEquals(2, entries.size());
    assertEquals("Blitz", entries.get(0).playerName());
    assertEquals(450, entries.get(0).score());
    assertEquals(11L, entries.get(0).time());
    assertEquals("Hard", entries.get(0).difficulty());
    assertEquals("de", entries.get(0).languageCode());

    assertNull(entries.get(1).playerName());
    assertEquals(300, entries.get(1).score());
    assertEquals(12L, entries.get(1).time());
    assertNull(entries.get(1).difficulty());
    assertNull(entries.get(1).languageCode());
  }

  @Test
  void allTimePreservesExistingSql() throws Exception {
    AtomicReference<String> capturedSql = new AtomicReference<>();

    RankingQueryService.loadAllTime(
        connectionReturning(
            capturedSql,
            List.of(new Row("Astra", 300, 15L, "Normal", "en"))
        )
    );

    assertEquals(
        "SELECT player_name, score, time, difficulty, lang_code, played_at " +
            "FROM scores " +
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT 10",
        capturedSql.get()
    );
  }

  @Test
  void monthlyPreservesExistingSql() throws Exception {
    AtomicReference<String> capturedSql = new AtomicReference<>();

    RankingQueryService.loadMonthly(
        connectionReturning(
            capturedSql,
            List.of(new Row("Comet", 300, 12L, "Easy", "ja"))
        )
    );

    assertEquals(
        "SELECT player_name, score, time, difficulty, lang_code, played_at " +
            "FROM scores " +
            "WHERE YEAR(played_at) = YEAR(NOW()) " +
            "  AND MONTH(played_at) = MONTH(NOW()) " +
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT 10",
        capturedSql.get()
    );
  }

  private static Connection connectionReturning(
      AtomicReference<String> capturedSql,
      List<Row> rows
  ) {
    ClassLoader loader = RankingQueryServiceTest.class.getClassLoader();

    return (Connection) Proxy.newProxyInstance(
        loader,
        new Class<?>[] {Connection.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "prepareStatement" -> {
            capturedSql.set((String) args[0]);
            yield preparedStatementReturning(loader, rows);
          }
          case "close" -> null;
          case "isClosed" -> false;
          case "toString" -> "RankingQueryServiceTestConnection";
          default -> throw new UnsupportedOperationException(
              "Unexpected Connection method: " + method.getName()
          );
        }
    );
  }

  private static PreparedStatement preparedStatementReturning(
      ClassLoader loader,
      List<Row> rows
  ) {
    return (PreparedStatement) Proxy.newProxyInstance(
        loader,
        new Class<?>[] {PreparedStatement.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "executeQuery" -> resultSetReturning(loader, rows);
          case "close" -> null;
          case "isClosed" -> false;
          case "toString" -> "RankingQueryServiceTestPreparedStatement";
          default -> throw new UnsupportedOperationException(
              "Unexpected PreparedStatement method: " + method.getName()
          );
        }
    );
  }

  private static ResultSet resultSetReturning(
      ClassLoader loader,
      List<Row> rows
  ) {
    AtomicInteger cursor = new AtomicInteger(-1);

    return (ResultSet) Proxy.newProxyInstance(
        loader,
        new Class<?>[] {ResultSet.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "next" -> cursor.incrementAndGet() < rows.size();
          case "getString" -> {
            Row row = current(rows, cursor.get());
            String column = (String) args[0];
            yield switch (column) {
              case "player_name" -> row.playerName();
              case "difficulty" -> row.difficulty();
              case "lang_code" -> row.languageCode();
              default -> throw new UnsupportedOperationException(
                  "Unexpected string column: " + column
              );
            };
          }
          case "getInt" -> {
            Row row = current(rows, cursor.get());
            String column = (String) args[0];
            if (!"score".equals(column)) {
              throw new UnsupportedOperationException(
                  "Unexpected int column: " + column
              );
            }
            yield row.score();
          }
          case "getLong" -> {
            Row row = current(rows, cursor.get());
            String column = (String) args[0];
            if (!"time".equals(column)) {
              throw new UnsupportedOperationException(
                  "Unexpected long column: " + column
              );
            }
            yield row.time();
          }
          case "close" -> null;
          case "isClosed" -> false;
          case "wasNull" -> false;
          case "toString" -> "RankingQueryServiceTestResultSet";
          default -> throw new UnsupportedOperationException(
              "Unexpected ResultSet method: " + method.getName()
          );
        }
    );
  }

  private static Row current(List<Row> rows, int index) {
    if (index < 0 || index >= rows.size()) {
      throw new IllegalStateException("ResultSet cursor is not on a row.");
    }
    return rows.get(index);
  }

  private record Row(
      String playerName,
      int score,
      long time,
      String difficulty,
      String languageCode
  ) {
  }
}
