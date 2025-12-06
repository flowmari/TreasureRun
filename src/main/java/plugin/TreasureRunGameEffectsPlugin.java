package plugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;

public class TreasureRunGameEffectsPlugin implements Listener {

  private final JavaPlugin plugin;
  private final Map<Player, Integer> playerTreasureCount = new HashMap<>();
  private final int totalTreasures = 10;
  private final Random random = new Random();

  // DJイベントが既に走っているかどうか（多重発火ロック）
  private boolean djRunning = false;

  // MySQL 情報
  private final String DB_HOST = "minecraft_mysql";
  private final String DB_NAME = "treasureDB";
  private final String DB_USER = "user";
  private final String DB_PASSWORD = "password";
  private Connection connection;

  // 内蔵曲リスト
  private final Sound[] djTracks = new Sound[]{
      Sound.MUSIC_DISC_RELIC,
      Sound.MUSIC_DISC_CAT,
      Sound.MUSIC_DISC_BLOCKS,
      Sound.MUSIC_DISC_MALL,
      Sound.MUSIC_DISC_WAIT
  };

  private final Color[] fireworkColors = new Color[]{
      Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.FUCHSIA, Color.AQUA, Color.ORANGE
  };

  private final Particle[] particleTypes = new Particle[]{
      Particle.FIREWORKS_SPARK, Particle.SPELL, Particle.CLOUD, Particle.CRIT, Particle.END_ROD
  };

  private final int bpm = 140;
  private final long interval = 20L * 60 / bpm; // tick間隔

  public TreasureRunGameEffectsPlugin(JavaPlugin plugin) {
    this.plugin = plugin;
    connectMySQL();
    createTableIfNotExists();
  }

  // ✅ DJイベント全体が何tick続くか（MultiChest側で終点を揃える用）
  public long getDjTotalTicks() {
    // DJ runnable は「毎 interval tick」で tickCount を 1 ずつ進め、(tracks*16) 回で終了
    long loops = (long) djTracks.length * 16L;
    return loops * interval;
  }

  private void connectMySQL() {
    try {
      connection = DriverManager.getConnection(
          "jdbc:mysql://" + DB_HOST + ":3306/" + DB_NAME,
          DB_USER,
          DB_PASSWORD
      );
      plugin.getLogger().info("MySQL connection established successfully!");
    } catch (SQLException e) {
      plugin.getLogger().severe("Failed to connect to MySQL: " + e.getMessage());
    }
  }

  private void createTableIfNotExists() {
    if (connection == null) return;
    String sql = "CREATE TABLE IF NOT EXISTS player_treasure_count (" +
        "player_name VARCHAR(50) PRIMARY KEY," +
        "count INT NOT NULL)";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.executeUpdate();
    } catch (SQLException e) {
      plugin.getLogger().severe("Failed to create table: " + e.getMessage());
    }
  }

  // ======================
  // ゲーム開始時にプレイヤーカウントをリセット
  // ======================
  public void resetPlayerTreasureCount(Player player) {
    playerTreasureCount.put(player, 0);
    plugin.getLogger().info("Reset treasure count for player: " + player.getName());
  }

  // 互換：昔 MultiChestPlugin から呼んでいたメソッド
  // （今は MultiChestPlugin 側がTitle維持を担当するので、DJ側はTitle無し起動が安全）
  public void onAllTreasuresCollected(Player player) {
    if (djRunning) {
      plugin.getLogger().info("DJイベントはすでに実行中のため、onAllTreasuresCollected をスキップしました。");
      return;
    }
    triggerUltimateDJEvent(player, false); // ✅ 互換でも Title競合しないよう false
  }

  // ======================
  // チェスト取得時（カウントとミニ演出のみ）
  // ======================
  public void onTreasureFound(Player player, Block block) {
    if (block != null && block.getType() == Material.CHEST) {

      // ミニ演出のみ（ゲーム終了判定は MultiChestPlugin 側）
      playMiniDJEffect(player);

      int count = playerTreasureCount.getOrDefault(player, 0) + 1;
      playerTreasureCount.put(player, count);

      saveTreasureCountToDB(player, count);

      // ★ここではゲーム終了判定はしない
    }
  }

  private void saveTreasureCountToDB(Player player, int count) {
    if (connection == null) return;
    String sql = "INSERT INTO player_treasure_count (player_name, count) VALUES (?, ?) " +
        "ON DUPLICATE KEY UPDATE count = ?";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setString(1, player.getName());
      stmt.setInt(2, count);
      stmt.setInt(3, count);
      stmt.executeUpdate();
    } catch (SQLException e) {
      plugin.getLogger().severe("Failed to save treasure count: " + e.getMessage());
    }
  }

  private void playMiniDJEffect(Player player) {
    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);

    Particle p = particleTypes[random.nextInt(particleTypes.length)];
    player.getWorld().spawnParticle(p, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);

    Firework fw = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK);
    FireworkMeta meta = fw.getFireworkMeta();
    Color color = fireworkColors[random.nextInt(fireworkColors.length)];
    meta.addEffect(org.bukkit.FireworkEffect.builder()
        .withColor(color)
        .withFade(Color.WHITE)
        .with(org.bukkit.FireworkEffect.Type.BALL)
        .build());
    meta.setPower(1);
    fw.setFireworkMeta(meta);
  }

  // 互換：旧シグネチャ（必要なら呼べる）
  public void triggerUltimateDJEvent(Player player) {
    triggerUltimateDJEvent(player, true);
  }

  /**
   * ✅ 新：Titleを出す/出さないを切り替え可能
   * showTitle=true  : 旧挙動（DJ側 Title あり）
   * showTitle=false : 新挙動（MultiChest側の Score/Time/Rank Title 維持と競合させない）
   */
  public void triggerUltimateDJEvent(Player player, boolean showTitle) {
    if (player == null || !player.isOnline()) return;

    if (djRunning) {
      plugin.getLogger().info("DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。");
      return;
    }
    djRunning = true;

    if (showTitle) {
      player.sendTitle("🎵 Treasure Complete! 🎵",
          "全ての宝物を発見しました！",
          10, 70, 20);
    }
    player.sendMessage("§6Congratulations! 全ての宝物を見つけました！");

    new BukkitRunnable() {
      int tickCount = 0;
      int trackIndex = 0;

      @Override
      public void run() {
        if (!player.isOnline()) {
          djRunning = false;
          this.cancel();
          return;
        }

        if (tickCount >= djTracks.length * 16) {
          djRunning = false;
          this.cancel();
          return;
        }

        spawnUltimateClubEffects(player);

        if (tickCount % 16 == 0) {
          Sound track = djTracks[trackIndex % djTracks.length];
          player.playSound(player.getLocation(), track, 1.0f, 1.0f);
          trackIndex++;
        }

        tickCount++;
      }

      private void spawnUltimateClubEffects(Player player) {
        Firework fw = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK);
        FireworkMeta meta = fw.getFireworkMeta();
        Color c1 = fireworkColors[random.nextInt(fireworkColors.length)];
        Color c2 = fireworkColors[random.nextInt(fireworkColors.length)];
        meta.addEffect(org.bukkit.FireworkEffect.builder()
            .withColor(c1, c2)
            .withFade(Color.WHITE)
            .with(org.bukkit.FireworkEffect.Type.values()[random.nextInt(org.bukkit.FireworkEffect.Type.values().length)])
            .build());
        meta.setPower(1 + random.nextInt(2));
        fw.setFireworkMeta(meta);

        Particle p1 = particleTypes[random.nextInt(particleTypes.length)];
        Particle p2 = particleTypes[random.nextInt(particleTypes.length)];
        player.getWorld().spawnParticle(p1, player.getLocation(), 100, 1, 1, 1, 0.2);
        player.getWorld().spawnParticle(p2, player.getLocation(), 80, 1, 1, 1, 0.1);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
      }
    }.runTaskTimer(plugin, 0L, interval);
  }

  // ======================
  // 自動チェスト取得検知イベント（※MultiChestと競合するため「無効化」のまま）
  // ======================
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent e) {
    // ★ここはあえて何もしません（TreasureRunMultiChestPlugin の onInventoryOpen が本体）
  }
}