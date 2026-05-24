package com.flowmari.treasurerun.rankingapi.readmodel;

import com.flowmari.treasurerun.rankingapi.api.LeaderboardEntryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RankingReadRepository {

  private final JdbcTemplate jdbcTemplate;

  public RankingReadRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<LeaderboardEntryResponse> findAllTimeLeaders(int limit) {
    String sql = """
        SELECT name, score, wins, best_time_ms, lang_code
        FROM alltime_scores
        ORDER BY score DESC,
                 wins DESC,
                 CASE WHEN best_time_ms IS NULL THEN 1 ELSE 0 END,
                 best_time_ms ASC,
                 name ASC
        LIMIT ?
        """;

    return jdbcTemplate.query(
        sql,
        (resultSet, rowNumber) -> new LeaderboardEntryResponse(
            resultSet.getString("name"),
            resultSet.getInt("score"),
            resultSet.getInt("wins"),
            resultSet.getObject("best_time_ms", Long.class),
            resultSet.getString("lang_code")
        ),
        limit
    );
  }
}
