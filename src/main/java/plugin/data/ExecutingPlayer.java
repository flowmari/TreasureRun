package plugin.data;

import org.apache.ibatis.type.Alias;

/**
 * EnemyDownのゲームを実行する際のプレイヤー情報を扱うオブジェクト。
 * プレイヤー名、合計点数、ゲームの残り時間などの情報を持つ。
 */
@Alias("ExecutingPlayer")  // ★ MyBatisのtypeAliasとして登録
public class ExecutingPlayer {
  private String playerName;
  private int score;
  private int gameTime;  // ゲームの残り時間を管理するためのフィールド

  public ExecutingPlayer() {
    this.playerName = "";
    this.score = 0;
    this.gameTime = 0;
  }

  public ExecutingPlayer(String playerName, int score) {
    this.playerName = playerName;
    this.score = Math.max(0, score);
    this.gameTime = 0;
  }

  public ExecutingPlayer(String playerName, int score, int gameTime) {
    this.playerName = playerName;
    this.score = Math.max(0, score);
    this.gameTime = Math.max(0, gameTime);
  }

  public String getPlayerName() {
    return playerName;
  }

  public void setPlayerName(String playerName) {
    this.playerName = playerName;
  }

  public int getScore() {
    return score;
  }

  public void setScore(int score) {
    this.score = Math.max(0, score);  // マイナスのスコアにならないように
  }

  public int getGameTime() {
    return gameTime;
  }

  public void setGameTime(int gameTime) {
    this.gameTime = Math.max(0, gameTime);  // ゲーム時間がマイナスにならないように
  }
}