package plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.IntStream;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSourceFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.type.StringTypeHandler;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import plugin.KitCommand;
import plugin.data.ExecutingPlayer;
import plugin.EnemyDownCommand;
import plugin.mapper.PlayerScoreMapper;
import plugin.mapper.data.PlayerScore;

public final class Main extends JavaPlugin implements Listener, CommandExecutor {

  private int count = 0;
  private final Map<UUID, Integer> playerScores = new HashMap<>();
  private final Map<UUID, ExecutingPlayer> playerScoreData = new HashMap<>();

  private File scoreFile;
  private int gameTime = 20;
  private BukkitRunnable gameTimer;

  private final Map<UUID, List<Entity>> spawnEntityMap = new HashMap<>();

  // MyBatis関連のフィールド
  private SqlSessionFactory sqlSessionFactory;
  private Connection connection;
  private boolean useMyBatis = true; // MyBatis使用フラグ（従来のYAML方式との切り替え用）

  // 新しく追加されたstatic MyBatis関連のフィールド
  private static SqlSessionFactory staticSqlSessionFactory;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    // MyBatis初期化
    initializeMyBatis();

    // 新しく追加された初期化処理
    initializeStaticMyBatis();

    scoreFile = new File(getDataFolder(), "scores.yml");
    if (!scoreFile.exists()) {
      try {
        scoreFile.getParentFile().mkdirs();
        scoreFile.createNewFile();
      } catch (IOException e) {
        getLogger().warning("スコアファイルの作成に失敗しました: " + e.getMessage());
      }
    }

    loadScores();

    String configMessage = getConfig().getString("Message");
    if (configMessage != null) {
      getLogger().info("Configメッセージ: " + configMessage);
    }

    Bukkit.getPluginManager().registerEvents(this, this);

    if (getCommand("kit") != null) getCommand("kit").setExecutor(new KitCommand());
    if (getCommand("setlevel") != null) getCommand("setlevel").setExecutor(this);
    if (getCommand("allsetlevel") != null) getCommand("allsetlevel").setExecutor(this);

    EnemyDownCommand enemyDownCommand = new EnemyDownCommand(this);
    if (getCommand("enemydown") != null) getCommand("enemydown").setExecutor(enemyDownCommand);
    getServer().getPluginManager().registerEvents(enemyDownCommand, this);

    plugin.EnemySpawnCommand enemySpawnCommand = new plugin.EnemySpawnCommand(this);
    if (getCommand("enemyspawn") != null) getCommand("enemyspawn").setExecutor(enemySpawnCommand);
    getServer().getPluginManager().registerEvents(enemySpawnCommand, this);

    // reloadenemydown コマンド登録
    if (getCommand("reloadenemydown") != null) {
      getCommand("reloadenemydown").setExecutor((sender, command, label, args) -> {
        reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "EnemyDownプラグインの設定を再読み込みしました。");
        return true;
      });
    }

    // enemystats コマンド登録
    if (getCommand("enemystats") != null) {
      getCommand("enemystats").setExecutor((sender, command, label, args) -> {
        if (!(sender instanceof Player player)) {
          sender.sendMessage("このコマンドはプレイヤー専用です。");
          return true;
        }
        UUID uuid = player.getUniqueId();
        ExecutingPlayer score = playerScoreData.get(uuid);
        if (score != null) {
          player.sendMessage(ChatColor.GOLD + "===== あなたの統計 =====");
          player.sendMessage(ChatColor.YELLOW + "名前: " + score.getPlayerName());
          player.sendMessage(ChatColor.YELLOW + "スコア: " + score.getScore());
          player.sendMessage(ChatColor.YELLOW + "残り時間: " + score.getGameTime() + "秒");
        } else {
          player.sendMessage(ChatColor.RED + "スコアデータが見つかりません。");
        }
        return true;
      });
    }

    // MyBatis専用コマンド追加
    if (getCommand("dbstats") != null) {
      getCommand("dbstats").setExecutor((sender, command, label, args) -> {
        if (!(sender instanceof Player player)) {
          sender.sendMessage("このコマンドはプレイヤー専用です。");
          return true;
        }
        return handleDbStats(player);
      });
    }

    if (getCommand("toggledb") != null) {
      getCommand("toggledb").setExecutor((sender, command, label, args) -> {
        useMyBatis = !useMyBatis;
        sender.sendMessage(ChatColor.GREEN + "データベース使用モード: " + (useMyBatis ? "MyBatis" : "YAML"));
        return true;
      });
    }

    startGameTimer();

    getLogger().info("EnemyDownプラグインが有効化されました。MyBatis: " + (sqlSessionFactory != null ? "有効" : "無効"));
  }

  @Override
  public void onDisable() {
    saveScores();
    if (gameTimer != null) {
      gameTimer.cancel();
    }

    // MyBatisリソースのクリーンアップ
    closeMyBatisResources();

    getLogger().info("EnemyDownプラグインが無効化されました。");
  }

  /**
   * 新しく追加されたstatic MyBatis初期化メソッド
   */
  private void initializeStaticMyBatis() {
    try {
      Reader reader = Resources.getResourceAsReader("mybatis-config.xml");
      staticSqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
      getLogger().info("Static MyBatis初期化成功！");
    } catch (Exception e) {
      getLogger().warning("Static MyBatis初期化に失敗しました: " + e.getMessage());
      // XMLファイルが見つからない場合はプログラマティック設定
      createStaticMyBatisProgrammatically();
    }
  }

  /**
   * Static MyBatisプログラマティック設定
   */
  private void createStaticMyBatisProgrammatically() {
    try {
      org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

      // データソース設定
      PooledDataSourceFactory dataSourceFactory = new PooledDataSourceFactory();
      Properties props = new Properties();
      props.setProperty("driver", "org.sqlite.JDBC");
      props.setProperty("url", "jdbc:sqlite:" + getDataFolder() + "/static_scores.db");
      props.setProperty("username", "");
      props.setProperty("password", "");
      dataSourceFactory.setProperties(props);

      JdbcTransactionFactory transactionFactory = new JdbcTransactionFactory();
      Environment environment = new Environment("static", transactionFactory, dataSourceFactory.getDataSource());

      configuration.setEnvironment(environment);
      configuration.addMapper(PlayerScoreMapper.class);
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setUseGeneratedKeys(true);

      staticSqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
      getLogger().info("Static MyBatisプログラマティック設定完了");

    } catch (Exception e) {
      getLogger().severe("Static MyBatisプログラマティック設定に失敗しました: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * MyBatis初期化メソッド
   */
  private void initializeMyBatis() {
    try {
      // データベース接続設定
      String url = getConfig().getString("database.url", "jdbc:sqlite:" + getDataFolder() + "/scores.db");
      String username = getConfig().getString("database.username", "");
      String password = getConfig().getString("database.password", "");

      // SQLite接続の初期化
      connection = DriverManager.getConnection(url);

      // テーブル作成
      createTables();

      // MyBatis設定ファイルの読み込み
      String resource = "mybatis-config.xml";
      InputStream inputStream;

      try {
        inputStream = Resources.getResourceAsStream(resource);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        // ✅ Mapper登録を明示的に追加（ここが重要）
        sqlSessionFactory.getConfiguration().addMapper(PlayerScoreMapper.class);

        getLogger().info("MyBatis設定ファイルから初期化完了");
      } catch (IOException e) {
        // 設定ファイルが見つからない場合は、プログラマティックに設定
        getLogger().info("MyBatis設定ファイルが見つかりません。プログラマティック設定を使用します。");
        createMyBatisProgrammatically();
        return;
      }

    } catch (Exception e) {
      getLogger().severe("MyBatis初期化に失敗しました: " + e.getMessage());
      e.printStackTrace();
      useMyBatis = false; // 失敗時はYAMLモードにフォールバック
    }
  }

  /**
   * プログラマティックMyBatis設定
   */
  private void createMyBatisProgrammatically() {
    try {
      org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

      // データソース設定
      PooledDataSourceFactory dataSourceFactory = new PooledDataSourceFactory();

      Properties props = new Properties();
      props.setProperty("driver", "org.sqlite.JDBC");
      props.setProperty("url", "jdbc:sqlite:" + getDataFolder() + "/scores.db");
      props.setProperty("username", "");
      props.setProperty("password", "");

      dataSourceFactory.setProperties(props);

      JdbcTransactionFactory transactionFactory = new JdbcTransactionFactory();

      Environment environment = new Environment("development", transactionFactory, dataSourceFactory.getDataSource());

      configuration.setEnvironment(environment);
      configuration.addMapper(PlayerScoreMapper.class);

      // UUIDを文字列として扱う設定（重要）
      configuration.getTypeHandlerRegistry().register(java.util.UUID.class, StringTypeHandler.class);

      // MyBatis設定最適化
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setUseGeneratedKeys(true);
      configuration.setCacheEnabled(true);
      configuration.setLazyLoadingEnabled(false);
      configuration.setAggressiveLazyLoading(false);

      sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

      getLogger().info("MyBatisプログラマティック設定完了");

    } catch (Exception e) {
      getLogger().severe("MyBatisプログラマティック設定に失敗しました: " + e.getMessage());
      useMyBatis = false;
    }
  }

  /**
   * データベーステーブル作成
   */
  private void createTables() {
    try {
      String createTableSql = """
          CREATE TABLE IF NOT EXISTS player_scores (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              uuid TEXT UNIQUE NOT NULL,
              player_name TEXT NOT NULL,
              score INTEGER DEFAULT 0,
              game_time INTEGER DEFAULT 0,
              difficulty TEXT DEFAULT 'NORMAL',
              registered_dt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              updated_dt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
          )
          """;

      connection.createStatement().execute(createTableSql);
      getLogger().info("データベーステーブルの初期化完了");

    } catch (SQLException e) {
      getLogger().severe("テーブル作成に失敗しました: " + e.getMessage());
    }
  }

  /**
   * MyBatisリソースのクリーンアップ
   */
  private void closeMyBatisResources() {
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
      }
    } catch (SQLException e) {
      getLogger().warning("データベース接続のクローズに失敗しました: " + e.getMessage());
    }
  }

  /**
   * MyBatisを使用したスコア保存
   */
  private void saveScoresWithMyBatis() {
    if (sqlSessionFactory == null) return;

    try (SqlSession session = sqlSessionFactory.openSession()) {
      PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);

      for (Map.Entry<UUID, ExecutingPlayer> entry : playerScoreData.entrySet()) {
        UUID uuid = entry.getKey();
        ExecutingPlayer ps = entry.getValue();

        PlayerScore playerScore = new PlayerScore();
        playerScore.setUuid(uuid);
        playerScore.setPlayerName(ps.getPlayerName());
        playerScore.setScore(ps.getScore());
        playerScore.setGameTime(ps.getGameTime());
        playerScore.setDifficulty("NORMAL");

        // 既存レコードの確認と更新/挿入
        PlayerScore existing = mapper.selectByUuid(uuid);
        if (existing != null) {
          playerScore.setId(existing.getId());
          mapper.updatePlayerScore(playerScore);
        } else {
          mapper.insertPlayerScore(playerScore);
        }
      }

      session.commit();
      getLogger().info("MyBatisでスコアデータを保存しました");

    } catch (Exception e) {
      getLogger().severe("MyBatisスコア保存に失敗しました: " + e.getMessage());
    }
  }

  /**
   * MyBatisを使用したスコア読み込み
   */
  private void loadScoresWithMyBatis() {
    if (sqlSessionFactory == null) return;

    try (SqlSession session = sqlSessionFactory.openSession()) {
      PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);

      List<PlayerScore> scores = mapper.selectAll();

      for (PlayerScore score : scores) {
        UUID uuid = score.getUuid();
        playerScores.put(uuid, score.getScore());

        ExecutingPlayer ps = new ExecutingPlayer();
        ps.setPlayerName(score.getPlayerName());
        ps.setScore(score.getScore());
        ps.setGameTime((int) score.getGameTime());
        playerScoreData.put(uuid, ps);
      }

      getLogger().info("MyBatisでスコアデータを読み込みました: " + scores.size() + "件");

    } catch (Exception e) {
      getLogger().severe("MyBatisスコア読み込みに失敗しました: " + e.getMessage());
    }
  }

  /**
   * データベース統計コマンドハンドラー
   */
  private boolean handleDbStats(Player player) {
    if (sqlSessionFactory == null) {
      player.sendMessage(ChatColor.RED + "MyBatisが初期化されていません。");
      return true;
    }

    try (SqlSession session = sqlSessionFactory.openSession()) {
      PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);

      List<PlayerScore> topScores = mapper.selectTopScores(10);

      player.sendMessage(ChatColor.GOLD + "===== データベース統計 =====");
      player.sendMessage(ChatColor.YELLOW + "総プレイヤー数: " + mapper.countAllPlayers());
      player.sendMessage(ChatColor.YELLOW + "最高スコア: " + (topScores.isEmpty() ? 0 : topScores.get(0).getScore()));
      player.sendMessage(ChatColor.GREEN + "===== トップ10 =====");

      for (PlayerScore ps : topScores) {
        player.sendMessage(
            ps.getId() + " | "
                + ps.getPlayerName() + " | "
                + ps.getScore() + " | "
                + ps.getDifficulty() + " | "
                + ps.getRegistered_dt()
        );
      }

    } catch (Exception e) {
      player.sendMessage(ChatColor.RED + "データベース統計の取得に失敗しました: " + e.getMessage());
      getLogger().severe("データベース統計取得エラー: " + e.getMessage());
    }

    return true;
  }

  private void startGameTimer() {
    gameTimer = new BukkitRunnable() {
      @Override
      public void run() {
        if (gameTime <= 0) {
          Bukkit.broadcastMessage(ChatColor.GREEN + "⏰ タイマー終了！");
          cancel();
          return;
        }
        Bukkit.broadcastMessage(ChatColor.YELLOW + "⏳ 残り時間: " + gameTime + " 秒");
        gameTime -= 5;
      }
    };
    gameTimer.runTaskTimer(this, 0, 5 * 20L);
  }

  public int getGameTime() {
    return gameTime;
  }

  public Map<UUID, List<Entity>> getSpawnEntityMap() {
    return spawnEntityMap;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    switch (command.getName().toLowerCase()) {
      case "setlevel":
        return handleSetLevel(sender, args);
      case "allsetlevel":
        return handleAllSetLevel(sender, args);
      default:
        return false;
    }
  }

  private boolean handleSetLevel(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("このコマンドはプレイヤー専用です。");
      return true;
    }
    if (args.length != 1) {
      player.sendMessage("/setlevel <level>");
      return true;
    }
    try {
      int level = Integer.parseInt(args[0]);
      player.setLevel(level);
      player.sendMessage("あなたのレベルを " + level + " に設定しました。");
    } catch (NumberFormatException e) {
      player.sendMessage("数値を入力してください。");
    }
    return true;
  }

  private boolean handleAllSetLevel(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("このコマンドはプレイヤー専用です。");
      return true;
    }
    if (args.length != 1) {
      player.sendMessage("/allsetlevel <level>");
      return true;
    }
    try {
      int level = Integer.parseInt(args[0]);
      for (Player p : Bukkit.getOnlinePlayers()) {
        p.setLevel(level);
        p.sendMessage("全員のレベルを " + level + " に設定しました。");
      }
    } catch (NumberFormatException e) {
      player.sendMessage("数値を入力してください。");
    }
    return true;
  }

  @EventHandler
  public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {
    count++;
    Player player = e.getPlayer();
    World world = player.getWorld();

    List<Color> colorList = List.of(Color.RED, Color.WHITE, Color.BLUE, Color.BLACK);
    BigInteger nextPrime = new BigInteger("1").nextProbablePrime();

    if (nextPrime.isProbablePrime(10)) {
      player.sendMessage("見つけた素数は " + nextPrime + " です！");

      if (count % 2 == 0) {
        player.sendMessage("偶数回スニークです！カラフルな花火を打ち上げます！");

        for (Color color : colorList) {
          Firework firework = world.spawn(player.getLocation(), Firework.class);
          FireworkMeta meta = firework.getFireworkMeta();

          meta.addEffect(FireworkEffect.builder()
              .withColor(color)
              .with(FireworkEffect.Type.BALL_LARGE)
              .withFlicker()
              .withTrail()
              .build());

          meta.setPower(3);
          firework.setFireworkMeta(meta);
        }

        try {
          Path filePath = Path.of("sneak_log.txt");
          String logEntry = player.getName() + " がスニークしました。\n";
          Files.writeString(filePath, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
          ex.printStackTrace();
          player.sendMessage("ファイル書き込みに失敗しました: " + ex.getMessage());
        }
      }
    }
  }

  @EventHandler
  public void onPlayerBedEnter(PlayerBedEnterEvent e) {
    Player player = e.getPlayer();
    ItemStack[] itemStacks = player.getInventory().getContents();

    IntStream.range(0, itemStacks.length)
        .filter(i -> {
          ItemStack item = itemStacks[i];
          return item != null && item.getType() == Material.DIAMOND_SWORD;
        })
        .findFirst()
        .ifPresent(i -> {
          player.sendMessage(ChatColor.GREEN + "ダイヤモンドソードを所持していますね！");
          // 例えばソードにエンチャントを付与する処理など追加可能
          ItemStack sword = itemStacks[i];
          sword.addEnchantment(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, 1);
          player.getInventory().setItem(i, sword);
        });
  }

  /**
   * スコアの読み込み処理
   */
  private void loadScores() {
    if (useMyBatis) {
      loadScoresWithMyBatis();
    } else {
      // YAML方式（未使用なら空でもOK）
    }
  }

  /**
   * スコアの保存処理
   */
  private void saveScores() {
    if (useMyBatis) {
      saveScoresWithMyBatis();
    } else {
      // YAML方式（未使用なら空でもOK）
    }
  }

} // ← Main クラスの終わり（必ず1個だけにする）