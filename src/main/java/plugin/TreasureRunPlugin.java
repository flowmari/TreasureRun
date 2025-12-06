package plugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.Random;

public class TreasureRunPlugin extends JavaPlugin implements Listener, TabExecutor {

  private Connection connection;

  private boolean isRunning = false;
  private long startTime;
  private Location chestWithTreasure;
  private Material targetMaterial;
  private String difficulty = "Normal";
  private BossBar bossBar;
  private int taskId;

  private final int GAME_TIME_SECONDS = 60;  // デフォルト60秒

  // --- config.yml から読み込む変数 ---
  private Location startLocation;
  private Location endLocation;
  private int easyTimeLimit;
  private int normalTimeLimit;
  private int hardTimeLimit;
  private String treasureItemName;
  private int treasureChestCount;
  private int otherChestCountEasy;
  private int otherChestCountNormal;
  private int otherChestCountHard;
  private int chestSpawnRadius;
  private boolean restorePlayerStatus;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    Bukkit.getPluginManager().registerEvents(this, this);
    getCommand("gameStart").setExecutor(this);
    getCommand("gameRank").setExecutor(this);
    setupDatabase();
    loadConfigValues();

    getLogger().info("TreasureRunPlugin が有効化されました");
  }

  @Override
  public void onDisable() {
    if (bossBar != null) bossBar.removeAll();
    try {
      if (connection != null) connection.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    getLogger().info("TreasureRunPlugin が無効化されました");
  }

  private void setupDatabase() {
    try {
      File dbFile = new File(getDataFolder(), "treasure_scores.db");
      if (!getDataFolder().exists()) getDataFolder().mkdir();
      connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
      Statement stmt = connection.createStatement();
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS scores (" +
          "id INTEGER PRIMARY KEY AUTOINCREMENT," +
          "player_name TEXT," +
          "score INTEGER," +
          "time INTEGER," +
          "difficulty TEXT" +
          ");");
      stmt.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private void saveScore(String playerName, int score, long timeSec, String difficulty) {
    try {
      PreparedStatement ps = connection.prepareStatement(
          "INSERT INTO scores (player_name, score, time, difficulty) VALUES (?, ?, ?, ?)");
      ps.setString(1, playerName);
      ps.setInt(2, score);
      ps.setLong(3, timeSec);
      ps.setString(4, difficulty);
      ps.executeUpdate();
      ps.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private void showRanking(Player player) {
    try {
      Statement stmt = connection.createStatement();
      ResultSet rs = stmt.executeQuery(
          "SELECT player_name, score, time, difficulty FROM scores ORDER BY score DESC LIMIT 10");
      player.sendMessage("=== TreasureRun ランキング TOP10 ===");
      int rank = 1;
      while (rs.next()) {
        player.sendMessage(rank + ". " + rs.getString("player_name") +
            " - " + rs.getInt("score") + "点 (" +
            rs.getLong("time") + "秒, 難易度:" +
            rs.getString("difficulty") + ")");
        rank++;
      }
      rs.close();
      stmt.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private void loadConfigValues() {
    String startWorldName = getConfig().getString("startLocation.world");
    World startWorld = Bukkit.getWorld(startWorldName);
    double startX = getConfig().getDouble("startLocation.x");
    double startY = getConfig().getDouble("startLocation.y");
    double startZ = getConfig().getDouble("startLocation.z");
    if (startWorld != null) startLocation = new Location(startWorld, startX, startY, startZ);
    else getLogger().warning("startLocationのワールドが見つかりません: " + startWorldName);

    String endWorldName = getConfig().getString("endLocation.world");
    World endWorld = Bukkit.getWorld(endWorldName);
    double endX = getConfig().getDouble("endLocation.x");
    double endY = getConfig().getDouble("endLocation.y");
    double endZ = getConfig().getDouble("endLocation.z");
    if (endWorld != null) endLocation = new Location(endWorld, endX, endY, endZ);
    else getLogger().warning("endLocationのワールドが見つかりません: " + endWorldName);

    easyTimeLimit = getConfig().getInt("difficultySettings.Easy.timeLimit", 300);
    normalTimeLimit = getConfig().getInt("difficultySettings.Normal.timeLimit", 180);
    hardTimeLimit = getConfig().getInt("difficultySettings.Hard.timeLimit", 120);

    treasureItemName = getConfig().getString("treasureItem", "diamond");

    treasureChestCount = getConfig().getInt("chests.treasureChestCount", 1);
    otherChestCountEasy = getConfig().getInt("chests.otherChestCount.Easy", 3);
    otherChestCountNormal = getConfig().getInt("chests.otherChestCount.Normal", 5);
    otherChestCountHard = getConfig().getInt("chests.otherChestCount.Hard", 7);

    chestSpawnRadius = getConfig().getInt("chestSpawnRadius", 20);

    restorePlayerStatus = getConfig().getBoolean("restorePlayerStatus", true);

    getLogger().info("config.yml の設定を読み込みました。");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage("プレイヤーから実行してください");
      return true;
    }
    Player player = (Player) sender;

    if (cmd.getName().equalsIgnoreCase("gameRank")) {
      showRanking(player);
      return true;
    }

    if (cmd.getName().equalsIgnoreCase("gameStart")) {
      if (isRunning) {
        player.sendMessage(ChatColor.RED + "ゲームは既に実行中です。");
        return true;
      }

      // 難易度設定
      if (args.length >= 1) {
        String diff = args[0].toLowerCase();
        if (diff.equals("easy") || diff.equals("normal") || diff.equals("hard")) {
          difficulty = diff.substring(0, 1).toUpperCase() + diff.substring(1);
        } else {
          player.sendMessage(ChatColor.RED + "難易度は Easy, Normal, Hard のいずれかを指定してください。");
          return true;
        }
      } else difficulty = "Normal";

      // アイテムMaterialに変換
      try {
        targetMaterial = Material.valueOf(treasureItemName.toUpperCase());
      } catch (IllegalArgumentException e) {
        getLogger().warning("config.ymlのtreasureItemが不正です: " + treasureItemName + " → diamondに設定します");
        targetMaterial = Material.DIAMOND;
      }

      // 宝物チェストをランダムな場所に生成
      Location base = player.getLocation();
      Random random = new Random();
      int dx = random.nextInt(chestSpawnRadius * 2 + 1) - chestSpawnRadius;
      int dz = random.nextInt(chestSpawnRadius * 2 + 1) - chestSpawnRadius;
      Location chestLoc = base.clone().add(dx, 0, dz);

      // 宝箱を地面に安全に生成
      spawnTreasureChest(chestLoc, targetMaterial);

      // 判定用に保存
      chestWithTreasure = chestLoc.clone();
      chestWithTreasure.setY(chestLoc.getWorld().getHighestBlockYAt(chestLoc));

      player.sendMessage(ChatColor.GREEN + "宝物は " + targetMaterial.name() + " です。周辺を探してください！");

      startGame(player);

      return true;
    }

    return false;
  }

  private void startGame(Player player) {
    isRunning = true;
    startTime = System.currentTimeMillis();

    int gameTimeSeconds;
    switch (difficulty.toLowerCase()) {
      case "easy":
        gameTimeSeconds = easyTimeLimit;
        break;
      case "hard":
        gameTimeSeconds = hardTimeLimit;
        break;
      default:
        gameTimeSeconds = normalTimeLimit;
    }

    bossBar = Bukkit.createBossBar("残り時間", BarColor.GREEN, BarStyle.SOLID);
    bossBar.addPlayer(player);

    final int totalTicks = gameTimeSeconds * 20;
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
      int ticksPassed = 0;

      @Override
      public void run() {
        if (!isRunning) {
          bossBar.removeAll();
          Bukkit.getScheduler().cancelTask(taskId);
          return;
        }
        ticksPassed++;
        double progress = (double) (totalTicks - ticksPassed) / totalTicks;
        bossBar.setProgress(progress);

        if (ticksPassed % 20 == 0) {
          int secondsLeft = gameTimeSeconds - (ticksPassed / 20);
          player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.YELLOW + "残り時間: " + secondsLeft + "秒"));
          if (secondsLeft == gameTimeSeconds) {
            player.sendMessage(ChatColor.RED + "制限時間は" + gameTimeSeconds + "秒です。");
          } else if (secondsLeft == 10) {
            player.sendMessage(ChatColor.RED + "あと10秒です！");
          }
        }

        if (ticksPassed >= totalTicks) {
          isRunning = false;
          bossBar.removeAll();
          player.sendMessage(ChatColor.RED + "時間切れ！宝物を見つけられませんでした。スコアは0点です。");
          saveScore(player.getName(), 0, gameTimeSeconds, difficulty);
          Bukkit.getScheduler().cancelTask(taskId);
        }
      }
    }, 0L, 1L);
  }

  @EventHandler
  public void onInventoryOpen(InventoryOpenEvent event) {
    if (!isRunning) return;
    if (!(event.getPlayer() instanceof Player)) return;

    Player player = (Player) event.getPlayer();
    Location opened = event.getInventory().getLocation();

    if (opened != null && opened.equals(chestWithTreasure)) {
      long elapsed = System.currentTimeMillis() - startTime;
      int score = calculateScore(elapsed);
      long timeSec = elapsed / 1000;

      player.sendMessage(ChatColor.GOLD + "おめでとう！ " + targetMaterial.name() + " を見つけました！");
      player.sendMessage(ChatColor.GREEN + "かかった時間: " + timeSec + "秒");
      player.sendMessage(ChatColor.GREEN + "スコア: " + score + "点");

      saveScore(player.getName(), score, timeSec, difficulty);

      isRunning = false;
      bossBar.removeAll();
      Bukkit.getScheduler().cancelTask(taskId);
    }
  }

  private int calculateScore(long elapsedMillis) {
    long seconds = elapsedMillis / 1000;

    if (difficulty.equalsIgnoreCase("Hard")) {
      if (seconds <= 30) return 150;
      else if (seconds <= 60) return 100;
      else return 50;
    } else if (difficulty.equalsIgnoreCase("Easy")) {
      if (seconds <= 30) return 80;
      else if (seconds <= 60) return 50;
      else return 20;
    } else { // Normal
      if (seconds <= 30) return 120;
      else if (seconds <= 60) return 80;
      else return 40;
    }
  }

  // --- 宝箱生成メソッド ---
  public void spawnTreasureChest(Location loc, Material material) {
    if (loc == null || loc.getWorld() == null) return;

    int x = loc.getBlockX();
    int z = loc.getBlockZ();
    World world = loc.getWorld();

    int y = world.getHighestBlockYAt(x, z);

    Block block = world.getBlockAt(x, y, z);
    block.setType(Material.CHEST);

    if (block.getState() instanceof Chest chest) {
      chest.getInventory().clear();
      chest.getInventory().addItem(new ItemStack(material, 1));
      chest.update();
    }

    getLogger().info("TreasureRun: 宝箱を " + x + "," + y + "," + z + " に生成しました。");
  }
}