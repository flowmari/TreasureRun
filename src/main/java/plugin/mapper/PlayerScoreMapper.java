package plugin.mapper;

import org.apache.ibatis.annotations.*;
import plugin.mapper.data.PlayerScore;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlayerScoreMapper {

  @Select("SELECT * FROM player_score")
  @Results({
      @Result(property = "id", column = "id"),
      @Result(property = "playerName", column = "player_name"),
      @Result(property = "score", column = "score"),
      @Result(property = "difficulty", column = "difficulty"),
      @Result(property = "registered_dt", column = "registered_dt"),
      @Result(property = "registeredAt", column = "registered_at"),
      @Result(property = "gameTime", column = "game_time"),
      @Result(property = "uuid", column = "uuid", javaType = UUID.class),
      @Result(property = "killCount", column = "kill_count")
  })
  List<PlayerScore> selectAll();

  @Select("SELECT * FROM player_score WHERE player_name = #{playerName}")
  @Results({
      @Result(property = "id", column = "id"),
      @Result(property = "playerName", column = "player_name"),
      @Result(property = "score", column = "score"),
      @Result(property = "difficulty", column = "difficulty"),
      @Result(property = "registered_dt", column = "registered_dt"),
      @Result(property = "registeredAt", column = "registered_at"),
      @Result(property = "gameTime", column = "game_time"),
      @Result(property = "uuid", column = "uuid", javaType = UUID.class),
      @Result(property = "killCount", column = "kill_count")
  })
  List<PlayerScore> selectByPlayerName(@Param("playerName") String playerName);

  @Select("SELECT * FROM player_score WHERE player_name = #{playerName} LIMIT 1")
  @Results({
      @Result(property = "id", column = "id"),
      @Result(property = "playerName", column = "player_name"),
      @Result(property = "score", column = "score"),
      @Result(property = "difficulty", column = "difficulty"),
      @Result(property = "registered_dt", column = "registered_dt"),
      @Result(property = "registeredAt", column = "registered_at"),
      @Result(property = "gameTime", column = "game_time"),
      @Result(property = "uuid", column = "uuid", javaType = UUID.class),
      @Result(property = "killCount", column = "kill_count")
  })
  PlayerScore selectPlayer(@Param("playerName") String playerName);

  @Select("SELECT * FROM player_score WHERE uuid = #{uuid, jdbcType=VARCHAR}")
  @Results({
      @Result(property = "id", column = "id"),
      @Result(property = "playerName", column = "player_name"),
      @Result(property = "score", column = "score"),
      @Result(property = "difficulty", column = "difficulty"),
      @Result(property = "registered_dt", column = "registered_dt"),
      @Result(property = "registeredAt", column = "registered_at"),
      @Result(property = "gameTime", column = "game_time"),
      @Result(property = "uuid", column = "uuid", javaType = UUID.class),
      @Result(property = "killCount", column = "kill_count")
  })
  PlayerScore selectByUuid(@Param("uuid") UUID uuid);

  @Select("SELECT * FROM player_score ORDER BY score DESC LIMIT #{limit}")
  @Results({
      @Result(property = "id", column = "id"),
      @Result(property = "playerName", column = "player_name"),
      @Result(property = "score", column = "score"),
      @Result(property = "difficulty", column = "difficulty"),
      @Result(property = "registered_dt", column = "registered_dt"),
      @Result(property = "registeredAt", column = "registered_at"),
      @Result(property = "gameTime", column = "game_time"),
      @Result(property = "uuid", column = "uuid", javaType = UUID.class),
      @Result(property = "killCount", column = "kill_count")
  })
  List<PlayerScore> selectTopScores(@Param("limit") int limit);

  @Select("SELECT COUNT(*) FROM player_score WHERE uuid = #{uuid, jdbcType=VARCHAR}")
  int existsByUuid(@Param("uuid") UUID uuid);

  @Select("SELECT COUNT(*) FROM player_score")
  int countAllPlayers();

  @Insert("INSERT INTO player_score (" +
      "player_name, score, difficulty, registered_dt, registered_at, game_time, uuid, kill_count" +
      ") VALUES (" +
      "#{playerName}, #{score}, #{difficulty}, #{registered_dt}, #{registeredAt}, #{gameTime}, #{uuid, jdbcType=VARCHAR}, #{killCount}" +
      ")")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insertPlayerScore(PlayerScore playerScore);

  @Insert("insert into player_score(player_name, score, difficulty, registered_dt) values (#{playerName}, #{score}, #{difficulty}, now())")
  int insert(PlayerScore playerScore);

  @Update("UPDATE player_score SET " +
      "player_name = #{playerName}, " +
      "score = #{score}, " +
      "difficulty = #{difficulty}, " +
      "registered_dt = #{registered_dt}, " +
      "registered_at = #{registeredAt}, " +
      "game_time = #{gameTime}, " +
      "kill_count = #{killCount} " +
      "WHERE uuid = #{uuid, jdbcType=VARCHAR}")
  int updatePlayerScore(PlayerScore playerScore);

  @Update("UPDATE player_score SET score = #{score}, registered_dt = #{registered_dt} WHERE id = #{id}")
  void updateScore(PlayerScore playerScore);

  default int update(PlayerScore playerScore) {
    return updatePlayerScore(playerScore);
  }

  @Delete("DELETE FROM player_score WHERE uuid = #{uuid, jdbcType=VARCHAR}")
  void deleteByUuid(@Param("uuid") UUID uuid);
}