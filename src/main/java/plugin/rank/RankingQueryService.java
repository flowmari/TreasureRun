package plugin.rank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared read boundary for the existing in-plugin leaderboard views.
 *
 * <p>This extraction deliberately preserves the current {@code /gameRank}
 * query semantics. It does not choose a new ranking model, change schema,
 * or introduce a new command.</p>
 */
public final class RankingQueryService {

  private static final String WEEKLY_SQL =
      "SELECT player_name, score, time, difficulty, lang_code, played_at " +
          "FROM scores " +
          "WHERE played_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
          "ORDER BY score DESC, time ASC, id DESC " +
          "LIMIT 10";

  private static final String ALL_TIME_SQL =
      "SELECT player_name, score, time, difficulty, lang_code, played_at " +
          "FROM scores " +
          "ORDER BY score DESC, time ASC, id DESC " +
          "LIMIT 10";

  private static final String MONTHLY_SQL =
      "SELECT player_name, score, time, difficulty, lang_code, played_at " +
          "FROM scores " +
          "WHERE YEAR(played_at) = YEAR(NOW()) " +
          "  AND MONTH(played_at) = MONTH(NOW()) " +
          "ORDER BY score DESC, time ASC, id DESC " +
          "LIMIT 10";

  private RankingQueryService() {
  }

  public static List<RankingEntry> loadWeekly(Connection connection) throws SQLException {
    return query(connection, WEEKLY_SQL);
  }

  public static List<RankingEntry> loadAllTime(Connection connection) throws SQLException {
    return query(connection, ALL_TIME_SQL);
  }

  public static List<RankingEntry> loadMonthly(Connection connection) throws SQLException {
    return query(connection, MONTHLY_SQL);
  }

  private static List<RankingEntry> query(Connection connection, String sql) throws SQLException {
    Objects.requireNonNull(connection, "connection");

    List<RankingEntry> entries = new ArrayList<>();

    try (PreparedStatement statement = connection.prepareStatement(sql);
         ResultSet resultSet = statement.executeQuery()) {

      while (resultSet.next()) {
        entries.add(
            new RankingEntry(
                resultSet.getString("player_name"),
                resultSet.getInt("score"),
                resultSet.getLong("time"),
                resultSet.getString("difficulty"),
                resultSet.getString("lang_code")
            )
        );
      }
    }

    return List.copyOf(entries);
  }

  public record RankingEntry(
      String playerName,
      int score,
      long time,
      String difficulty,
      String languageCode
  ) {
  }
}
