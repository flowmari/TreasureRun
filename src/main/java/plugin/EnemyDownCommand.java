package plugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.ibatis.session.SqlSession;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import plugin.data.ExecutingPlayer;
import plugin.mapper.PlayerScoreMapper;
import plugin.mapper.data.PlayerScore;

public class EnemyDownCommand extends BaseCommand implements Listener, CommandExecutor {

  private final Main main;
  private final DBUtils dbUtils = new DBUtils();

  // プレイヤーごとの残りゲーム時間（秒）
  private final Map<UUID, Integer> playerGameTimes = new HashMap<>();
  // プレイヤーごとのゲーム開始時刻（ミリ秒）
  private final Map<UUID, Long> playerStartTimes = new HashMap<>();
  // スポーンさせた敵エンティティ
  private final Map<UUID, List<Entity>> spawnEntityMap = new HashMap<>();
  // プレイヤーの難易度
  private final Map<UUID, String> playerDifficultyMap = new HashMap<>();
  // 現在のスコア
  private final Map<UUID, Integer> playerCurrentScores = new HashMap<>();
  // 実行中プレイヤーデータ
  public final Map<UUID, ExecutingPlayer> playerScoreData = new HashMap<>();

  public static final String EASY = "easy";
  public static final String NORMAL = "normal";
  public static final String HARD = "hard";
  public static final String NONE = "none";
  public static final String LIST = "list";
  public static final String MYBATIS_LIST = "mybatislist";

  private static final int GAME_TIME = 30; // 秒

  // 宝箱アイテムとボーナス点
  private final Map<Material, Integer> treasureBonus = Map.of(
      Material.GOLD_INGOT, 100,
      Material.DIAMOND, 500,
      Material.EMERALD, 250
  );

  public EnemyDownCommand(Main main) {
    this.main = main;
    Bukkit.getPluginManager().registerEvents(this, main);
  }

  @Override
  public boolean onExecuteNPCCommand(CommandSender sender, Command command, String label, String[] args) {
    sender.sendMessage("このコマンドはプレイヤー専用です。");
    return true;
  }

  @Override
  public boolean onExecutePlayerCommand(Player player, Command command, String label, String[] args) {
    if (args.length == 1 && (args[0].equalsIgnoreCase(LIST) || args[0].equalsIgnoreCase(MYBATIS_LIST))) {
      return handleMyBatisListCommand(player);
    }

    if (args.length == 0) {
      player.sendMessage(ChatColor.RED + "難易度を指定してください: easy / normal / hard");
      return false;
    }

    if (args[0].equalsIgnoreCase("gamestart")) {
      return handleGameStart(player);
    }

    if (playerGameTimes.containsKey(player.getUniqueId())) {
      player.sendMessage(ChatColor.RED + "既にゲーム中です");
      return true;
    }

    String difficulty = getDifficulty(player, args);
    if (difficulty.equals(NONE)) return false;

    PlayerScore nowPlayerScore = getOrCreatePlayerScoreWithMyBatis(player);
    nowPlayerScore.setScore(0);
    nowPlayerScore.setGameTime((long) GAME_TIME);
    nowPlayerScore.setDifficulty(difficulty);

    playerGameTimes.put(player.getUniqueId(), GAME_TIME);
    playerStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
    spawnEntityMap.put(player.getUniqueId(), new ArrayList<>());
    playerDifficultyMap.put(player.getUniqueId(), difficulty);
    playerCurrentScores.put(player.getUniqueId(), 0);

    player.sendMessage(ChatColor.YELLOW + "[DEBUG] 難易度: " + difficulty);
    gamePlay(player, nowPlayerScore, difficulty);
    return true;
  }

  private boolean handleGameStart(Player player) {
    if (playerGameTimes.containsKey(player.getUniqueId())) {
      player.sendMessage(ChatColor.RED + "既にゲーム中です");
      return true;
    }

    Material treasureMaterial = getRandomTreasureMaterial();
    Location treasureLocation = getRandomLocation(player, player.getWorld());

    // アイテムを配置
    player.getWorld().dropItem(treasureLocation, new ItemStack(treasureMaterial));

    // 開始時刻と残り時間を記録
    playerGameTimes.put(player.getUniqueId(), GAME_TIME);
    playerStartTimes.put(player.getUniqueId(), System.currentTimeMillis());

    player.sendMessage(ChatColor.YELLOW + "宝探しゲーム開始！ " + treasureMaterial.name() + " を探せ！");

    return true;
  }

  private boolean handleMyBatisListCommand(Player player) {
    try (SqlSession session = dbUtils.getSqlSessionFactory().openSession()) {
      PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);
      List<PlayerScore> playerScoreList = mapper.selectAll();
      for (PlayerScore playerScore : playerScoreList) {
        player.sendMessage(playerScore.getId() + "｜" +
            playerScore.getPlayerName() + "｜" +
            playerScore.getScore() + "｜" +
            playerScore.getDifficulty() + "｜" +
            (playerScore.getRegistered_dt() != null ? playerScore.getRegistered_dt() : "null"));
      }
    } catch (Exception e) {
      main.getLogger().severe("MyBatis list error: " + e.getMessage());
    }
    return true;
  }

  private void gamePlay(Player player, PlayerScore nowPlayerScore, String difficulty) {
    UUID uuid = player.getUniqueId();
    List<Entity> spawnList = spawnEntityMap.get(uuid);

    new BukkitRunnable() {
      @Override
      public void run() {
        if (!player.isOnline() || nowPlayerScore.getGameTime() <= 0) {
          cancel();
          endGame(player);
          return;
        }
        EntityType enemy = getEnemy(difficulty);
        Entity mob = player.getWorld().spawnEntity(player.getLocation(), enemy);
        if (mob instanceof LivingEntity living) {
          living.setCustomName(player.getName() + "_enemy");
          living.setCustomNameVisible(false);
        }
        spawnList.add(mob);
        nowPlayerScore.setGameTime(nowPlayerScore.getGameTime() - 5);
        playerGameTimes.put(uuid, (int) nowPlayerScore.getGameTime());
        player.sendMessage("⚔ 敵出現: " + enemy.name());
      }
    }.runTaskTimer(main, 0L, 5 * 20L);
  }

  private EntityType getEnemy(String difficulty) {
    return switch (difficulty) {
      case NORMAL -> List.of(EntityType.ZOMBIE, EntityType.SKELETON).get(new Random().nextInt(2));
      case HARD -> List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITCH).get(new Random().nextInt(3));
      default -> EntityType.ZOMBIE;
    };
  }

  private void endGame(Player player) {
    UUID uuid = player.getUniqueId();
    int score = playerCurrentScores.getOrDefault(uuid, 0);
    String difficulty = playerDifficultyMap.getOrDefault(uuid, NORMAL);

    // タイトル表示
    player.sendTitle("終了", player.getName() + " スコア: " + score, 10, 70, 20);

    // エンティティ削除
    for (Entity e : spawnEntityMap.getOrDefault(uuid, new ArrayList<>())) {
      if (e != null && !e.isDead()) e.remove();
    }

    // スコア保存
    saveScore(player, score, difficulty);

    // 後始末
    playerGameTimes.remove(uuid);
    playerStartTimes.remove(uuid);
    spawnEntityMap.remove(uuid);
    playerDifficultyMap.remove(uuid);
    playerCurrentScores.remove(uuid);

    player.sendTitle("ゲームが終了しました。", player.getName() + " 合計: " + score + " 点！", 0, 60, 0);
  }

  private PlayerScore getOrCreatePlayerScoreWithMyBatis(Player player) {
    try (SqlSession session = dbUtils.getSqlSessionFactory().openSession(true)) {
      PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);
      List<PlayerScore> list = mapper.selectByPlayerName(player.getName());
      if (!list.isEmpty()) return list.get(0);

      PlayerScore newScore = new PlayerScore();
      newScore.setPlayerName(player.getName());
      newScore.setScore(0);
      newScore.setDifficulty("");
      newScore.setRegistered_dt(nowString());
      newScore.setUuid(player.getUniqueId());
      mapper.insertPlayerScore(newScore);
      return newScore;
    } catch (Exception e) {
      main.getLogger().severe("getOrCreate error: " + e.getMessage());
      return null;
    }
  }

  private void saveScore(Player player, int score, String difficulty) {
    try (SqlSession session = dbUtils.getSqlSessionFactory().openSession(true)) {
      PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);
      List<PlayerScore> list = mapper.selectByPlayerName(player.getName());
      PlayerScore ps;
      if (!list.isEmpty()) {
        ps = list.get(0);
        ps.setScore(score);
        ps.setRegistered_dt(nowString());
        mapper.updateScore(ps);
      } else {
        ps = new PlayerScore();
        ps.setPlayerName(player.getName());
        ps.setScore(score);
        ps.setDifficulty(difficulty);
        ps.setRegistered_dt(nowString());
        ps.setUuid(player.getUniqueId());
        mapper.insertPlayerScore(ps);
      }
      player.sendMessage(ChatColor.GREEN + "✅ スコアが保存されました！");
    } catch (Exception e) {
      e.printStackTrace();
      player.sendMessage(ChatColor.RED + "⚠️ スコア保存に失敗しました。");
    }
  }

  private String getDifficulty(Player player, String[] args) {
    return switch (args[0].toLowerCase()) {
      case EASY, NORMAL, HARD -> args[0].toLowerCase();
      default -> {
        player.sendMessage(ChatColor.RED + "不正な難易度です");
        yield NONE;
      }
    };
  }

  private Material getRandomTreasureMaterial() {
    List<Material> materials = new ArrayList<>(treasureBonus.keySet());
    return materials.get(new Random().nextInt(materials.size()));
  }

  private Location getRandomLocation(Player player, World world) {
    int radius = 50;
    Random random = new Random();
    int x = (int) (player.getLocation().getX() + (random.nextDouble() * 2 * radius) - radius);
    int z = (int) (player.getLocation().getZ() + (random.nextDouble() * 2 * radius) - radius);
    return new Location(world, x, world.getHighestBlockYAt(x, z) + 1, z);
  }

  private String nowString() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  }

  @EventHandler
  public void onEntityDeath(EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer == null) return;
    UUID uuid = killer.getUniqueId();
    if (!playerCurrentScores.containsKey(uuid)) return;

    String difficulty = playerDifficultyMap.getOrDefault(uuid, NORMAL);
    int addedScore = switch (difficulty) {
      case EASY -> 10;
      case NORMAL -> 20;
      case HARD -> 30;
      default -> 10;
    };

    int newScore = playerCurrentScores.get(uuid) + addedScore;
    playerCurrentScores.put(uuid, newScore);
    killer.sendMessage(ChatColor.GREEN + "敵を倒した！スコア: " + newScore);
  }

  @EventHandler
  public void onEntityPickup(PlayerPickupItemEvent event) {
    Player player = event.getPlayer();  // すでに Player型なので、instanceofは不要
    UUID uuid = player.getUniqueId();
    if (!playerGameTimes.containsKey(uuid)) return;

    ItemStack item = event.getItem().getItemStack();
    if (treasureBonus.containsKey(item.getType())) {
      long elapsedTime = System.currentTimeMillis() - playerStartTimes.getOrDefault(uuid, System.currentTimeMillis());
      int baseScore = (int) (10000 / (elapsedTime / 1000.0));
      int bonus = treasureBonus.get(item.getType());
      int totalScore = baseScore + bonus;

      player.sendMessage(ChatColor.GREEN + "宝を見つけた！ " + totalScore + " 点！");
      saveScore(player, totalScore, playerDifficultyMap.getOrDefault(uuid, NORMAL));

      // ゲーム終了
      endGame(player);
      event.getItem().remove();
    }
  }
}