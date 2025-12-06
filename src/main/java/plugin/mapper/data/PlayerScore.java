package plugin.mapper.data;

import java.io.Serializable;
import java.time.LocalDateTime; // ✅ Timestamp の代わりに使用
import java.util.UUID; // ✅ 追加

import lombok.Getter;               // ✅ Lombok追加
import lombok.Setter;               // ✅ Lombok追加
import lombok.NoArgsConstructor;    // ✅ Lombok追加

@Getter
@Setter
@NoArgsConstructor
public class PlayerScore implements Serializable {

  private int id;
  private String playerName;
  private int score;
  private String difficulty;

  // データベース用スネークケース（旧式）
  private String registered_dt;

  // キャメルケース用（新式）→ LocalDateTime に変更 ✅
  private LocalDateTime registeredDt;
  private LocalDateTime registeredAt;

  // ゲーム内プレイ時間
  private long gameTime;

  // プレイヤー固有UUID（UUID型に変更 ✅）
  private UUID uuid;

  // name: playerName の別名としても利用されることがある
  private String name;

  // 拡張フィールド：キル数など
  private int killCount;

  // 明示的なデフォルトコンストラクタはLombokで補われるが、残してもOK
  // public PlayerScore() {} ← @NoArgsConstructorにより不要になるが保持しても問題なし

  // 追加されたコンストラクタ ✅
  public PlayerScore(String playerName, int score, String difficulty) {
    this.playerName = playerName;
    this.score = score;
    this.difficulty = difficulty;
  }

  // --- Getter / Setter ---
  // 既存の明示的メソッドもそのまま残す（構造を壊さないため）

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
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
    this.score = score;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String difficulty) {
    this.difficulty = difficulty;
  }

  public String getRegistered_dt() {
    return registered_dt;
  }

  public void setRegistered_dt(String registered_dt) {
    this.registered_dt = registered_dt;
  }

  public LocalDateTime getRegisteredDt() {
    return registeredDt;
  }

  public void setRegisteredDt(LocalDateTime registeredDt) {
    this.registeredDt = registeredDt;
  }

  public LocalDateTime getRegisteredAt() {
    return registeredAt;
  }

  public void setRegisteredAt(LocalDateTime registeredAt) {
    this.registeredAt = registeredAt;
  }

  public long getGameTime() {
    return gameTime;
  }

  public void setGameTime(long gameTime) {
    this.gameTime = gameTime;
  }

  public UUID getUuid() {  // ✅ UUID型で修正
    return uuid;
  }

  public void setUuid(UUID uuid) {  // ✅ UUID型で修正
    this.uuid = uuid;
  }

  public String getName() {
    return name != null ? name : playerName;
  }

  public void setName(String name) {
    this.name = name;
    this.playerName = name; // 同期的に playerName も更新
  }

  public int getKillCount() {
    return killCount;
  }

  public void setKillCount(int killCount) {
    this.killCount = killCount;
  }

  @Override
  public String toString() {
    return "PlayerScore{" +
        "id=" + id +
        ", playerName='" + playerName + '\'' +
        ", name='" + name + '\'' +
        ", score=" + score +
        ", difficulty='" + difficulty + '\'' +
        ", registered_dt='" + registered_dt + '\'' +
        ", registeredDt=" + registeredDt +
        ", registeredAt=" + registeredAt +
        ", gameTime=" + gameTime +
        ", uuid=" + uuid +
        ", killCount=" + killCount +
        '}';
  }
}