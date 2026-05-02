package plugin;

import plugin.GameStageManager;
import plugin.MovingSafetyZoneTask;
import plugin.RealtimeRankTicker;
import plugin.LangCommand;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList; // ✅ treasureReload: 古いListener解除
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.function.BooleanSupplier; // ✅ 追加（状態Supplier用）
import plugin.UfoCaravanController;

// ✅ ここに追加（Season）
import plugin.rank.SeasonRepository;
import plugin.rank.SeasonScoreRepository;

public class TreasureRunMultiChestPlugin extends JavaPlugin implements Listener, TabExecutor {

  // __MSZ_AUTO_START_ON_JOIN
  private final java.util.concurrent.atomic.AtomicBoolean __mszAutoStarted = new java.util.concurrent.atomic.AtomicBoolean(false);
  // ================================
  // DB接続
  // ================================
  private Connection connection;

  // =======================================================
  // ✅ ✅ ✅ 追加：ProverbLogRepository（Favorites機能の橋渡し）
  // =======================================================
  private ProverbLogRepository proverbLogRepository;

  // =======================================================
  // ✅ ✅ ✅ Favorites図鑑 + I18n + 19言語 完全対応セット
  // =======================================================
  private plugin.PlayerLanguageStore playerLanguageStore;
  private plugin.LanguageConfigStore languageConfigStore;
  private plugin.I18n i18n;
  private plugin.quote.QuoteModule quoteModule;

  // ================================
  // ゲーム状態
  // ================================
  private boolean isRunning = false;
  private long startTime;
  private String difficulty = "Normal";
  private BossBar bossBar;
  private int taskId = -1;

  // ✅ 終了Title維持タスク（DJ終点で止める）
  private int finishTitleTaskId = -1;

  // ✅ TIME_UP用：短時間だけTitle固定表示するタスク
  private int timeUpTitleTaskId = -1;

  private TreasureChestManager treasureChestManager;
  private TreasureRunGameEffectsPlugin treasureRunGameEffectsPlugin;
  private UfoCaravanController ufo;
  private GameStageManager gameStageManager;
  private Location currentStageCenter = null;

  private TreasureItemFactory itemFactory;
  private RankRewardManager rankRewardManager;

  // ✅ ここに追加（Weekly / All-time ランキング用）
  private SeasonRepository seasonRepository;
  private SeasonScoreRepository seasonScoreRepository;

  private int easyTimeLimit;
  private int normalTimeLimit;
  private int hardTimeLimit;

  private double easyMultiplier;
  private double normalMultiplier;
  private double hardMultiplier;

  private int easyPenalty;
  private int normalPenalty;
  private int hardPenalty;

  private final Map<String, Integer> treasureChestCounts = new HashMap<>();
  private final List<Material> treasurePool = new ArrayList<>();
  private int chestSpawnRadius;

  private RealtimeRankTicker rankTicker;
  public boolean rankDirty = true;

  private int totalChestsRemaining = 0;

  // ✅ 追加：開始時の総宝箱数（TIME'S UP の 7/10 表示に使う）
  private int totalChestsAtStart = 10;

  // ✅ 追加：ゲーム1回で設置する宝箱総数（config.yml の totalChests を読む）
  private int totalChests;

  // ✅ 追加：連続TIME_UPカウント（プレイヤーごと）
  // - 「連続で時間切れ」した回数を数える
  // - 3回目（3の倍数回）だけ TIME'S UP! → OVERCOMING ADVERSITY に切り替える
  private final Map<UUID, Integer> consecutiveTimeUpCounts = new HashMap<>();

  // =======================================================
  // ✅ 言語切替（LanguageStore / LanguageSelectGui）
  // - /treasureReload で GUI を作り直して Listener 再登録するため
  // =======================================================
  private LanguageStore languageStore;          // ✅ final じゃなくする（安全に運用）
  private LanguageSelectGui languageSelectGui;  // ✅ reloadで作り直す

  public LanguageStore getLanguageStore() { return languageStore; }
  public LanguageSelectGui getLanguageSelectGui() { return languageSelectGui; }
  public plugin.PlayerLanguageStore getPlayerLanguageStore() {
    return playerLanguageStore;
  }

  // i18n getter が欲しい場合
  public plugin.I18n getI18n() {
    return i18n;
  }

  private String uiLang(Player player) {
    String lang = getConfig().getString("language.default", "en");
    try {
      if (player != null && getPlayerLanguageStore() != null) {
        String saved = getPlayerLanguageStore().getLang(player, lang);
        if (saved != null && !saved.isBlank()) lang = saved;
      }
    } catch (Throwable ignored) {}
    return lang;
  }

  private String trPlayer(Player player, String key, I18n.Placeholder... placeholders) {
    String lang = uiLang(player);
    return getI18n().tr(lang, key, placeholders);
  }

  private String displayMaterialName(Player player, Material material) {
    String key = "items.material." + material.name().toLowerCase(Locale.ROOT);
    String value = trPlayer(player, key);
    if (value == null || value.isBlank() || value.equals(key) || value.startsWith("Translation missing:")) {
      return materialJapaneseNames.getOrDefault(material, material.name());
    }
    return value;
  }

  // ✅ GUIタイトル（LanguageSelectGuiと揃える：古いGUIを閉じる判定にも使う）
  private static final String LANGUAGE_GUI_TITLE = ChatColor.DARK_AQUA + "Language / 言語";

  private final Map<Material, String> materialJapaneseNames = new HashMap<>() {{
    put(Material.DIAMOND, "ダイヤモンド");
    put(Material.GOLD_INGOT, "金インゴット");
    put(Material.IRON_INGOT, "鉄インゴット");
    put(Material.EMERALD, "エメラルド");
    put(Material.APPLE, "リンゴ");
    put(Material.NETHERITE_INGOT, "ネザライトのインゴット");
    put(Material.LAPIS_LAZULI, "ラピスラズリ");
    put(Material.REDSTONE, "レッドストーン");
    put(Material.COAL, "石炭");
    put(Material.ENCHANTED_GOLDEN_APPLE, "エンチャント金リンゴ");
    put(Material.TNT, "TNT");
    put(Material.DIAMOND_BLOCK, "ダイヤモンドブロック");
    put(Material.GOLD_BLOCK, "金ブロック");
    put(Material.EMERALD_BLOCK, "エメラルドブロック");
    put(Material.IRON_BLOCK, "鉄ブロック");
  }};

  private final Map<UUID, Integer> playerScores = new HashMap<>();

  private final Map<UUID, Location> originalLocations = new HashMap<>();
  private long previousWorldTime = -1;
  private boolean previousStorm = false;
  private boolean previousThundering = false;

  // =======================================================
  // ✅ StartThemePlayer（ゲーム開始テーマ）
  // =======================================================
  private StartThemePlayer startThemePlayer;

  public StartThemePlayer getStartThemePlayer() {
    return startThemePlayer;
  }

  // =======================================================
  // ✅ バニラMinecraft BGM抑制用（MUSICカテゴリ）
  // =======================================================
  private final Map<UUID, BukkitTask> vanillaMusicSuppressTasks = new HashMap<>();

  // =======================================================
  // ✅ 近接サウンド（明るくなる）サービス
  // =======================================================
  private ChestProximitySoundService chestSound;

  // =======================================================
  // ✅ Heartbeat（鼓動）サービス
  // =======================================================
  private HeartbeatSoundService heartbeatSoundService;

  // ✅ Outcome（成功/時間切れ）ランダムSUBTITLE
  private final OutcomeMessageService outcomeMessageService = new OutcomeMessageService(this);

  // =======================================================
  // ✅ ✅ ✅ 追加：プレイヤーが選んだ言語を保持（proverb_logs保存に使う）
  // =======================================================
  private final Map<UUID, String> playerLastLang = new HashMap<>();

  @Override
  public void onEnable() {

    getLogger().info("🌈 TreasureRunMultiChestPlugin: 起動 🌈");

    saveDefaultConfig();
    reloadConfig();

    // ✅ 採用向け：Join自動スタートはデフォルトOFF（必要時だけON）
    boolean autoStart = getConfig().getBoolean("debug.autoStartOnJoin", false);

    if (autoStart) {
      // __MSZ_AUTO_START_ON_JOIN
      // 最初のプレイヤーが入った瞬間に1回だけ自動で gameStart Normal を実行（RCON不要）
      org.bukkit.Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
        @org.bukkit.event.EventHandler
        public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
          if (!__mszAutoStarted.compareAndSet(false, true)) return;
          final org.bukkit.entity.Player p = e.getPlayer();
          org.bukkit.Bukkit.getLogger().info("[MSZ] auto-start armed: player=" + p.getName());
          org.bukkit.Bukkit.getScheduler().runTaskLater(TreasureRunMultiChestPlugin.this, () -> {
            try {
              org.bukkit.Bukkit.dispatchCommand(p, "gameStart Normal");
              org.bukkit.Bukkit.getLogger().info("[MSZ] auto-start dispatched: gameStart Normal");
            } catch (Throwable t) {
              org.bukkit.Bukkit.getLogger().severe("[MSZ] auto-start failed: " + t);
            }
          }, 20L);
        }
      }, this);

      getLogger().warning("[MSZ] debug.autoStartOnJoin=true (auto-start enabled)");
    } else {
      getLogger().info("[MSZ] debug.autoStartOnJoin=false (auto-start disabled)");
    }

    Bukkit.getPluginManager().registerEvents(this, this);

    // ✅ 言語系：起動時初期化（store読み込み + GUI生成 + Listener登録）
    initOrReloadLanguageSystem(true);

    // ✅ ✅ ✅ 19言語 + Favorites図鑑 + I18n + DB自動判定 一式
    playerLanguageStore = new plugin.PlayerLanguageStore(this);

    languageConfigStore = new plugin.LanguageConfigStore();
    languageConfigStore.reloadFromConfig(getConfig());

    i18n = new plugin.I18n(this);
    i18n.loadOrCreate(); // languages/*.yml 読み込み（なければ生成）

    quoteModule = new plugin.quote.QuoteModule(this, playerLanguageStore, languageConfigStore, i18n);
    quoteModule.enable();

    // ✅ StartThemePlayer を生成して保持
    startThemePlayer = new StartThemePlayer(this);

    // ✅ ログアウトでBGM停止（事故防止）
    Bukkit.getPluginManager().registerEvents(new StartThemeStopListener(this), this);

    Bukkit.getPluginManager().registerEvents(new StageMobControlListener(this), this);

    // ✅ i18n: localize Minecraft/Spigot engine-generated death messages
    Bukkit.getPluginManager().registerEvents(new LocalizedDeathMessageListener(this), this);

    // ✅ i18n: localize Bukkit event-layer engine/system messages
    Bukkit.getPluginManager().registerEvents(new LocalizedSystemMessageListener(this), this);

    // ✅ i18n: ProtocolLib packet-layer audit/translation for Minecraft engine messages
    try {
      new plugin.LocalizedPacketMessageProtocolListener(this).enable();
    } catch (Throwable t) {
      getLogger().warning("[PacketI18n] Could not enable ProtocolLib packet listener: " + t.getMessage());
    }

    TreasureRunGameEffectsPlugin effects = new TreasureRunGameEffectsPlugin(this);
    Bukkit.getPluginManager().registerEvents(effects, this);
    this.treasureRunGameEffectsPlugin = effects;

    // ✅ gameStart / gamestart 両対応（gamestart を正式化）
    if (getCommand("gameStart") != null) {
      getCommand("gameStart").setExecutor(this);
      getCommand("gameStart").setTabCompleter(this);
    }
    if (getCommand("gamestart") != null) {
      getCommand("gamestart").setExecutor(this);
      getCommand("gamestart").setTabCompleter(this);
    }

    if (getCommand("gameRank") != null) {
      getCommand("gameRank").setExecutor(this);
    }
    if (getCommand("craftspecialemerald") != null) {
      getCommand("craftspecialemerald").setExecutor(new CraftSpecialEmeraldCommand(this));
    }
    if (getCommand("checktreasureemerald") != null) {
      getCommand("checktreasureemerald").setExecutor(new CheckTreasureEmeraldCommand(this));
    }
    if (getCommand("gameMenu") != null) {
      getCommand("gameMenu").setExecutor(this);
      getCommand("gameMenu").setTabCompleter(this); // ✅ 追加
    }

    // ✅✅✅ ここに追加：/gameEnd を有効化（MSZ停止・床復元が確実に走る）
    if (getCommand("gameEnd") != null) {
      getCommand("gameEnd").setExecutor(this);
    } else {
      getLogger().warning("⚠ /gameEnd が plugin.yml に見つかりません（commands: gameEnd を確認してください）");
    }

    // ✅ /lang：言語GUIを開く / 言語を変更する（LangCommand）※登録は1回に統一
    if (getCommand("lang") != null) {
      LangCommand langCmd = new LangCommand(this);
      getCommand("lang").setExecutor(langCmd);
      // TabCompleter を付けたい場合だけ（LangCommand が TabExecutor 実装している前提）
      // getCommand("lang").setTabCompleter(langCmd);
    } else {
      getLogger().warning("⚠ /lang が plugin.yml に見つかりません");
    }



    if (getCommand("clearStageBlocks") != null) {
      getCommand("clearStageBlocks").setExecutor(new StageCleanupCommand(this));
    }

    // ✅ ✅ ✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅
    // ✅ QuoteFavorite command register（旧登録は削除し、新登録に統一）
    if (getCommand("quoteFavorite") != null) {
      getCommand("quoteFavorite").setExecutor(new plugin.quote.QuoteFavoriteCommand(this));
      getCommand("quoteFavorite").setTabCompleter(new plugin.quote.QuoteFavoriteTabCompleter());
    } else {
      getLogger().warning("⚠ /quoteFavorite が plugin.yml に見つかりません");
    }
    // ✅ ✅ ✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅

    // ✅ treasureReload コマンド（plugin.yml にあれば拾う）
    if (getCommand("treasureReload") != null) {
      getCommand("treasureReload").setExecutor(this);
    }
    // ✅ Export: config.yml messages.translation.* → plugins/TreasureRun/languages/*.yml
    if (getCommand("treasureExportLang") != null) {
      getCommand("treasureExportLang").setExecutor(new TreasureExportLangCommand(this));
    } else {
      getLogger().warning("⚠ /treasureexportLang が plugin.yml に見つかりません");
    }

// ✅ Export: config.yml の messages.translation.* → languages/*.yml
    if (getCommand("treasureexportLang") != null) {
      getCommand("treasureexportLang").setExecutor(new TreasureExportLangCommand(this));
    } else {
      getLogger().warning("⚠ /treasureExportLang が plugin.yml に見つかりません");
    }

    setupDatabase();

    // ✅ ここに追加（Weekly/All-time ranking repositories）
    // ✅ DB migration: apply bundled SQL migrations before ranking repositories are used
    try {
      new MigrationRunner(this).runAll();
    } catch (Throwable t) {
      getLogger().warning("[Migration] startup migration failed: " + t.getMessage());
      t.printStackTrace();
    }

    this.seasonRepository = new SeasonRepository(this);
    this.seasonScoreRepository = new SeasonScoreRepository(this);

    // =======================================================
    // ✅ ✅ ✅ 追加：ProverbLogRepository 生成（Favorites橋渡し）
    // =======================================================
    proverbLogRepository = new ProverbLogRepository(this);

    // ✅ ✅ ✅ ✅ ✅ 追加：QuoteFavoriteBookClickListener を登録（Favoritesの本クリック対応）
    getServer().getPluginManager().registerEvents(new QuoteFavoriteBookClickListener(this), this);

    // ✅ ✅ ✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅
    // ✅ Hybrid Listener register（Sneak+RightClick shortcut）
    getServer().getPluginManager().registerEvents(new plugin.quote.QuoteFavoriteShortcutListener(this), this);
    // ✅ ✅ ✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅✅

    loadConfigValues();

    this.treasureChestManager =
        new TreasureChestManager(this, treasureChestCounts, treasurePool, chestSpawnRadius);

    // ✅ 近接サウンドサービス生成（treasureChestManager 作成の「後」）
    this.chestSound = new ChestProximitySoundService(
        this,
        this.treasureChestManager,
        () -> this.isRunning
    );

    // ✅ Heartbeat: サービス生成（onEnableで生成）
    this.heartbeatSoundService = new HeartbeatSoundService(this);

    // 宝箱取得演出
    Bukkit.getPluginManager().registerEvents(new TreasureChestPickupListener(this), this);

    this.itemFactory = new TreasureItemFactory(this);

    this.ufo = new UfoCaravanController(this);

    this.gameStageManager = new GameStageManager(this, this.ufo);
    Bukkit.getPluginManager().registerEvents(this.gameStageManager, this);
    getLogger().info("[TreasureRun] GameStageManager event registered!");

    // ✅✅✅ 追加：サーバー起動直後に「Treasure Shop」残骸を全削除（再起動残り対策）
    this.gameStageManager.purgeTreasureShopEntitiesOnStartup();

    this.rankRewardManager = new RankRewardManager(this);

    // ✅ /rank は常に登録（ON/OFFは RankDebugCommand 側で rankDebug.enabled を見る）
    if (getCommand("rank") != null) {
      getCommand("rank").setExecutor(new RankDebugCommand(this));
      getLogger().info("✅ /rank executor registered");
    } else {
      getLogger().warning("⚠ /rank が plugin.yml に見つかりません（commands: rank を確認してください）");
    }

    CustomRecipeLoader recipeLoader = new CustomRecipeLoader(this);
    recipeLoader.registerRecipes();

    int rtInterval = getConfig().getInt("rankTicker.intervalSec", 10);
    int rtTopN = getConfig().getInt("rankTicker.topN", 10);
    int rtWidth = getConfig().getInt("rankTicker.tickerWidth", 32);
    rankTicker = new RealtimeRankTicker(this, rtInterval, rtTopN, rtWidth);
    rankTicker.start();

    try {
      // ❌ MovingSafetyZoneTask は GameStageManager 側で「1箇所だけ」起動・停止・復元を管理する
      //    ここで runTaskTimer(1L,1L) すると多重起動＋period不一致で床が広範囲に塗られる原因になる
      // new MovingSafetyZoneTask(this).runTaskTimer(this, 1L, 1L);
      getLogger().info("[MSZ] (skipped) scheduled by GameStageManager only");
    } catch (Throwable t) {
      getLogger().warning("[MSZ] schedule failed: " + t.getMessage());
    }

    getLogger().info("✅ TreasureRunMultiChestPlugin が正常に起動しました！");
  }

  @Override
  public void onDisable() {
    if (startThemePlayer != null) startThemePlayer.stopAll();
    if (chestSound != null) chestSound.stopAll();
    if (heartbeatSoundService != null) heartbeatSoundService.stopAll();

    stopVanillaMusicSuppressAll();

    if (bossBar != null) bossBar.removeAll();

    if (finishTitleTaskId != -1) {
      Bukkit.getScheduler().cancelTask(finishTitleTaskId);
      finishTitleTaskId = -1;
    }

    if (timeUpTitleTaskId != -1) {
      Bukkit.getScheduler().cancelTask(timeUpTitleTaskId);
      timeUpTitleTaskId = -1;
    }

    // ✅ LanguageSelectGui の Listener 解除（停止時の後片付け）
    if (languageSelectGui != null) {
      HandlerList.unregisterAll(languageSelectGui);
      languageSelectGui = null;
    }

    try {
      if (connection != null) connection.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    if (rankTicker != null) rankTicker.stop();
    if (treasureChestManager != null) treasureChestManager.removeAllChests();

    if (gameStageManager != null) {
      gameStageManager.clearDifficultyBlocks();
      gameStageManager.clearShopEntities();
    }

    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }

    // =======================================================
    // ✅ ✅ ✅ 追加：Repository参照を明示的に切る（安全）
    // =======================================================
    proverbLogRepository = null;

    getLogger().info("🔻 TreasureRunMultiChestPlugin: 無効化");
  }

  // =======================================================
  // ✅ treasureReload: 言語ストア再読込 & GUI作り直し（古いListener解除）
  // =======================================================
  private void initOrReloadLanguageSystem(boolean registerListener) {
    if (languageStore == null) {
      languageStore = new LanguageStore();
    }

    // config → store
    languageStore.reloadFromConfig(getConfig());

    // 古いGUI Listener解除 → 作り直し → 再登録
    if (languageSelectGui != null) {
      HandlerList.unregisterAll(languageSelectGui);
    }
    languageSelectGui = new LanguageSelectGui(this, languageStore);

    if (registerListener) {
      getServer().getPluginManager().registerEvents(languageSelectGui, this);
    }
  }

  // ✅ 追加：ゲーム中にプレイヤーが抜けても残骸が残らないように掃除
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    if (!isRunning) return;

    // ✅✅✅ 追加：落ちても保存（WIN扱いにしない）
    try {
      UUID uuid = player.getUniqueId();
      int score = playerScores.getOrDefault(uuid, 0);
      long elapsedSec = Math.max(0, (System.currentTimeMillis() - startTime) / 1000L);

      saveScore(player, score, elapsedSec, difficulty);
      addSeasonScore(player, score, false, null);

      getLogger().info("[DB] saved on quit: player=" + player.getName()
          + " score=" + score + " time=" + elapsedSec + " diff=" + difficulty);
    } catch (Throwable t) {
      getLogger().warning("⚠ onQuit save failed: " + t.getMessage());
    }

    if (startThemePlayer != null) startThemePlayer.stop(player);
    stopVanillaMusicSuppress(player);
    if (heartbeatSoundService != null) heartbeatSoundService.stop(player);
    if (treasureRunGameEffectsPlugin != null) treasureRunGameEffectsPlugin.stopFinalCountdownBeeps(player);
    if (chestSound != null) chestSound.stop(player);

    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }

    if (finishTitleTaskId != -1) {
      Bukkit.getScheduler().cancelTask(finishTitleTaskId);
      finishTitleTaskId = -1;
    }

    if (timeUpTitleTaskId != -1) {
      Bukkit.getScheduler().cancelTask(timeUpTitleTaskId);
      timeUpTitleTaskId = -1;
    }

    if (bossBar != null) bossBar.removeAll();
    if (treasureChestManager != null) treasureChestManager.removeAllChests();

    if (gameStageManager != null) {
      gameStageManager.clearDifficultyBlocks();
      gameStageManager.clearShopEntities();
    }

    isRunning = false;
    playerScores.remove(player.getUniqueId());
    originalLocations.remove(player.getUniqueId());

    // ✅ ログアウト時：連続TIME_UPカウントは切る（事故防止）
    consecutiveTimeUpCounts.remove(player.getUniqueId());

    // ✅ ✅ ✅ 追加：ログアウト時に言語保持も消す（事故防止）
    playerLastLang.remove(player.getUniqueId());

    if (previousWorldTime >= 0) {
      World w = player.getWorld();
      w.setTime(previousWorldTime);
      w.setStorm(previousStorm);
      w.setThundering(previousThundering);
      previousWorldTime = -1;
    }
  }

  // =======================================================
  // MySQL 接続（自動再接続）
  // =======================================================
  public Connection getConnection() {
    try {
      if (connection == null || connection.isClosed() || !connection.isValid(1)) {
        getLogger().warning("⚠ MySQL 再接続を試みます…");
        reconnect();
      }
    } catch (SQLException e) {
      getLogger().warning("⚠ MySQL 接続チェック失敗: " + e.getMessage());
      reconnect();
    }
    return connection;
  }

  // =======================================================
  // ✅ ✅ ✅ 追加：QuoteFavoriteCommand が呼ぶ橋渡しメソッド
  // =======================================================
  public Connection getMySQLConnection() {
    return getConnection();
  }

  private void reconnect() {
    try {
      if (connection != null) {
        try { connection.close(); } catch (SQLException ignored) {}
      }

      String host = getConfig().getString("database.host", "localhost");
      String port = getConfig().getString("database.port", "3306");
      String database = getConfig().getString("database.database", "treasureDB");
      String username = getConfig().getString("database.user", "root");
      String password = getConfig().getString("database.password", "");

      connection = DriverManager.getConnection(
          "jdbc:mysql://" + host + ":" + port + "/" + database +
              "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
          username, password
      );

      getLogger().info("✅ MySQL 再接続成功！");
    } catch (SQLException e) {
      getLogger().severe("❌ MySQL 再接続失敗: " + e.getMessage());
    }
  }

  private void setupDatabase() {
    String host = getConfig().getString("database.host", "localhost");
    String port = getConfig().getString("database.port", "3306");
    String database = getConfig().getString("database.database", "treasureDB");
    String username = getConfig().getString("database.user", "root");
    String password = getConfig().getString("database.password", "");

    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
      connection = DriverManager.getConnection(
          "jdbc:mysql://" + host + ":" + port + "/" + database +
              "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
          username, password
      );

      // =========================
      // ✅ scores (new schema)
      // =========================
      try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS scores (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "uuid VARCHAR(36) NULL," +
                "player_name VARCHAR(50) NOT NULL," +
                "score INT NOT NULL," +
                "time BIGINT NOT NULL," +
                "difficulty VARCHAR(10) NOT NULL," +
                "lang_code VARCHAR(10) NOT NULL DEFAULT 'ja'," +
                "played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_scores_played_at (played_at)," +
                "INDEX idx_scores_diff_time (difficulty, time)," +
                "INDEX idx_scores_uuid_played (uuid, played_at)" +
                ");"
        );
      }

      // ✅ 既存DB（旧scores）向け migrate（列が無ければ追加）
      migrateScoresTable(connection);

      getLogger().info("✅ scores テーブル準備完了");

      // =========================
      // ✅ proverb_logs
      // =========================
      try (Statement ps = connection.createStatement()) {
        ps.executeUpdate(
            "CREATE TABLE IF NOT EXISTS proverb_logs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(50) NOT NULL," +
                "outcome VARCHAR(20) NOT NULL," +
                "difficulty VARCHAR(10) NOT NULL," +
                "lang VARCHAR(10) NOT NULL," +
                "quote_text TEXT NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_player_uuid_created_at (player_uuid, created_at)" +
                ");"
        );
      }

      getLogger().info("✅ proverb_logs テーブル準備完了");

    } catch (ClassNotFoundException | SQLException e) {
      getLogger().severe("❌ DB 初期化失敗");
      e.printStackTrace();
    }
  }

  private void migrateScoresTable(Connection conn) throws SQLException {
    if (conn == null) return;

    String existsSql =
        "SELECT COUNT(*) " +
            "FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scores' AND COLUMN_NAME = ?";

    java.util.function.Predicate<String> hasColumn = (String col) -> {
      try (PreparedStatement ps = conn.prepareStatement(existsSql)) {
        ps.setString(1, col);
        try (ResultSet rs = ps.executeQuery()) {
          return rs.next() && rs.getInt(1) > 0;
        }
      } catch (SQLException e) {
        getLogger().warning("⚠ migrateScoresTable column check failed: " + col + " " + e.getMessage());
        return false;
      }
    };

    try (Statement st = conn.createStatement()) {
      if (!hasColumn.test("uuid")) {
        st.executeUpdate("ALTER TABLE scores ADD COLUMN uuid VARCHAR(36) NULL");
      }
      if (!hasColumn.test("lang_code")) {
        st.executeUpdate("ALTER TABLE scores ADD COLUMN lang_code VARCHAR(10) NOT NULL DEFAULT 'ja'");
      }
      if (!hasColumn.test("played_at")) {
        st.executeUpdate("ALTER TABLE scores ADD COLUMN played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
      }
    }

    getLogger().info("✅ scores migration check done");
  }

  private void saveScore(String playerName, int score, long timeSec, String difficulty) {
    // Player名しか渡されない呼び出しもあるので、onlineから拾えるなら拾う
    Player p = Bukkit.getPlayerExact(playerName);

    String uuidStr = null;
    String langCode = "ja";

    // uuid
    if (p != null) {
      uuidStr = p.getUniqueId().toString();
    }

    // lang_code（あなたの環境は playerLanguageStore があるのでそれ優先）
    try {
      if (p != null && playerLanguageStore != null) {
        // ここはあなたの addSeasonScore と同じ取り方
        langCode = playerLanguageStore.getLang(p, "ja");
      }
    } catch (Throwable ignored) {}

    // フォールバック：beginGameStartAfterLanguageSelected で入れてる playerLastLang
    try {
      if ((langCode == null || langCode.isBlank()) && p != null) {
        String v = playerLastLang.get(p.getUniqueId());
        if (v != null && !v.isBlank()) langCode = v;
      }
    } catch (Throwable ignored) {}

    if (langCode == null || langCode.isBlank()) langCode = "ja";
    langCode = langCode.toLowerCase(Locale.ROOT);

    try (PreparedStatement ps = getConnection().prepareStatement(
        "INSERT INTO scores (uuid, player_name, score, time, difficulty, lang_code, played_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, NOW())"
    )) {
      ps.setString(1, uuidStr);
      ps.setString(2, playerName);
      ps.setInt(3, score);
      ps.setLong(4, timeSec);
      ps.setString(5, difficulty);
      ps.setString(6, langCode);
      ps.executeUpdate();

      rankDirty = true;

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  // ✅ 追加：Player から確実に uuid/lang を取って scores に保存する版
  private void saveScore(Player player, int score, long timeSec, String difficulty) {
    if (player == null) return;

    String lang = "ja";
    try {
      if (playerLanguageStore != null) {
        lang = playerLanguageStore.getLang(player, "ja");
      }
    } catch (Throwable ignored) {}

    if (lang == null || lang.isBlank()) lang = "ja";
    lang = lang.toLowerCase(Locale.ROOT);

    try (PreparedStatement ps = getConnection().prepareStatement(
        "INSERT INTO scores (uuid, player_name, score, time, difficulty, lang_code, played_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, NOW())"
    )) {
      ps.setString(1, player.getUniqueId().toString());
      ps.setString(2, player.getName());
      ps.setInt(3, score);
      ps.setLong(4, timeSec);
      ps.setString(5, difficulty);
      ps.setString(6, lang);

      ps.executeUpdate();
      rankDirty = true;

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private int getRunRank(String playerName, int score, long timeSec, String difficulty) {
    Connection conn = getConnection();
    if (conn == null) {
      getLogger().severe("❌ getRunRank: DB接続が null です");
      return -1;
    }

    String sql =
        "SELECT player_name, score, time, difficulty " +
            "FROM scores " +
            "WHERE UPPER(difficulty) = UPPER(?) " +
            "ORDER BY time ASC, score DESC";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, difficulty);

      try (ResultSet rs = ps.executeQuery()) {
        int rank = 0;
        while (rs.next()) {
          rank++;

          String name = rs.getString("player_name");
          int s       = rs.getInt("score");
          long t      = rs.getLong("time");
          String diff = rs.getString("difficulty");

          if (s == score &&
              t == timeSec &&
              diff != null && diff.equalsIgnoreCase(difficulty) &&
              name != null && name.equalsIgnoreCase(playerName)) {
            return rank;
          }
        }
      }

      getLogger().warning("⚠ getRunRank: 該当行が見つかりませんでした " +
          "(player=" + playerName +
          ", score=" + score +
          ", time=" + timeSec +
          ", difficulty=" + difficulty + ")");
    } catch (SQLException e) {
      getLogger().warning("⚠ ランキング計算中にエラー: " + e.getMessage());
      e.printStackTrace();
    }

    return -1;
  }

  private void showRanking(Player player) {
    try (PreparedStatement ps = getConnection().prepareStatement(
        "SELECT player_name, score, time, difficulty " +
            "FROM scores " +
            "ORDER BY time ASC, score DESC " +
            "LIMIT 10");
        ResultSet rs = ps.executeQuery()) {

      player.sendMessage(ChatColor.GOLD + trPlayer(player, "rank.command.timeTop10Title"));

      int rank = 1;
      while (rs.next()) {
        String name = rs.getString("player_name");
        int score = rs.getInt("score");
        long time = rs.getLong("time");
        String diff = rs.getString("difficulty");

        ChatColor diffColor = switch (diff) {
          case "Easy" -> ChatColor.GREEN;
          case "Normal" -> ChatColor.YELLOW;
          case "Hard" -> ChatColor.RED;
          default -> ChatColor.WHITE;
        };

        player.sendMessage(
            ChatColor.AQUA + trPlayer(
                player,
                "rank.command.timeTop10Line",
                I18n.Placeholder.of("{rank}", String.valueOf(rank)),
                I18n.Placeholder.of("{player}", String.valueOf(name)),
                I18n.Placeholder.of("{time}", String.valueOf(time)),
                I18n.Placeholder.of("{score}", String.valueOf(score)),
                I18n.Placeholder.of("{difficulty}", String.valueOf(diff))
            )
        );

        rank++;
      }

      if (rank == 1) {
        player.sendMessage(ChatColor.GRAY + trPlayer(player, "gameplay.pickup.scoreNone"));
      }

    } catch (SQLException e) {
      e.printStackTrace();
      player.sendMessage(ChatColor.RED + trPlayer(player, "rank.command.loadError"));
    }
  }

  // ✅ weekly を「直近7日（plays）」TOP10 にする版
  private void showWeeklyRanking(Player player) {
    Connection conn = getConnection();
    if (conn == null) {
      player.sendMessage(ChatColor.RED + trPlayer(player, "rank.command.dbUnavailable"));
      return;
    }

    String sql =
        "SELECT player_name, score, time, difficulty, lang_code, played_at " +
            "FROM scores " +
            "WHERE played_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT 10";

    player.sendMessage(ChatColor.AQUA + trPlayer(player, "rank.command.playsTitleWeekly"));

    try (PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {

      int rank = 1;
      while (rs.next()) {
        String name = rs.getString("player_name");
        int score = rs.getInt("score");
        long time = rs.getLong("time");
        String diff = rs.getString("difficulty");
        String lang = rs.getString("lang_code");

        player.sendMessage(
            ChatColor.AQUA + trPlayer(
                player,
                "rank.command.playsLine",
                I18n.Placeholder.of("{rank}", String.valueOf(rank)),
                I18n.Placeholder.of("{player}", String.valueOf(name == null ? "unknown" : name)),
                I18n.Placeholder.of("{score}", String.valueOf(score)),
                I18n.Placeholder.of("{time}", String.valueOf(time)),
                I18n.Placeholder.of("{difficulty}", String.valueOf(diff == null ? "-" : diff)),
                I18n.Placeholder.of("{lang}", String.valueOf(lang == null ? "ja" : lang))
            )
        );
        rank++;
      }

      if (rank == 1) {
        player.sendMessage(ChatColor.GRAY + trPlayer(player, "rank.command.weeklyEmpty"));
      } else {
        player.sendMessage(ChatColor.DARK_GRAY + trPlayer(player, "rank.command.weeklySwitch"));
      }

    } catch (SQLException e) {
      e.printStackTrace();
      player.sendMessage(ChatColor.RED + trPlayer(player, "rank.command.loadError"));
    }
  }

  private void showAllTimeRanking(Player player) {
    Connection conn = getConnection();
    if (conn == null) {
      player.sendMessage(ChatColor.RED + trPlayer(player, "rank.command.dbUnavailable"));
      return;
    }

    String sql =
        "SELECT player_name, score, time, difficulty, lang_code, played_at " +
            "FROM scores " +
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT 10";

    player.sendMessage(ChatColor.AQUA + trPlayer(player, "rank.command.playsTitleAllTime"));

    try (PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {

      int rank = 1;
      while (rs.next()) {
        String name = rs.getString("player_name");
        int score = rs.getInt("score");
        long time = rs.getLong("time");
        String diff = rs.getString("difficulty");
        String lang = rs.getString("lang_code");

        player.sendMessage(
            ChatColor.AQUA + trPlayer(
                player,
                "rank.command.playsLine",
                I18n.Placeholder.of("{rank}", String.valueOf(rank)),
                I18n.Placeholder.of("{player}", String.valueOf(name == null ? "unknown" : name)),
                I18n.Placeholder.of("{score}", String.valueOf(score)),
                I18n.Placeholder.of("{time}", String.valueOf(time)),
                I18n.Placeholder.of("{difficulty}", String.valueOf(diff == null ? "-" : diff)),
                I18n.Placeholder.of("{lang}", String.valueOf(lang == null ? "ja" : lang))
            )
        );
        rank++;
      }

      if (rank == 1) {
        player.sendMessage(ChatColor.GRAY + trPlayer(player, "rank.command.allTimeEmpty"));
      } else {
        player.sendMessage(ChatColor.DARK_GRAY + trPlayer(player, "rank.command.allTimeSwitch"));
      }

    } catch (SQLException e) {
      e.printStackTrace();
      player.sendMessage(ChatColor.RED + trPlayer(player, "rank.command.loadError"));
    }
  }

  private void showMonthlyRanking(Player player) {
    Connection conn = getConnection();
    if (conn == null) {
      player.sendMessage(ChatColor.RED + trPlayer(player, "rank.command.dbUnavailable"));
      return;
    }

    String sql =
        "SELECT player_name, score, time, difficulty, lang_code, played_at " +
            "FROM scores " +
            "WHERE YEAR(played_at) = YEAR(NOW()) " +
            "  AND MONTH(played_at) = MONTH(NOW()) " +
            "ORDER BY score DESC, time ASC, id DESC " +
            "LIMIT 10";

    player.sendMessage(ChatColor.AQUA + trPlayer(player, "rank.command.playsTitleMonthly"));

    try (PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {

      int rank = 1;
      while (rs.next()) {
        String name = rs.getString("player_name");
        int score = rs.getInt("score");
        long time = rs.getLong("time");
        String diff = rs.getString("difficulty");
        String lang = rs.getString("lang_code");

        player.sendMessage(
            ChatColor.AQUA + trPlayer(
                player,
                "rank.command.playsLine",
                I18n.Placeholder.of("{rank}", String.valueOf(rank)),
                I18n.Placeholder.of("{player}", String.valueOf(name == null ? "unknown" : name)),
                I18n.Placeholder.of("{score}", String.valueOf(score)),
                I18n.Placeholder.of("{time}", String.valueOf(time)),
                I18n.Placeholder.of("{difficulty}", String.valueOf(diff == null ? "-" : diff)),
                I18n.Placeholder.of("{lang}", String.valueOf(lang == null ? "ja" : lang))
            )
        );
        rank++;
      }

      if (rank == 1) {
        player.sendMessage(ChatColor.GRAY + trPlayer(player, "rank.command.monthlyEmpty"));
      } else {
        player.sendMessage(ChatColor.DARK_GRAY + trPlayer(player, "rank.command.monthlySwitch"));
      }

    } catch (SQLException e) {
      e.printStackTrace();
      player.sendMessage(ChatColor.RED + trPlayer(player, "rank.command.loadError"));
    }
  }

  private void loadConfigValues() {
    easyTimeLimit   = getConfig().getInt("difficultySettings.Easy.timeLimit", 300);
    normalTimeLimit = getConfig().getInt("difficultySettings.Normal.timeLimit", 180);
    hardTimeLimit   = getConfig().getInt("difficultySettings.Hard.timeLimit", 120);

    chestSpawnRadius = getConfig().getInt("chestSpawnRadius", 20);

    easyMultiplier   = getConfig().getDouble("difficultySettings.Easy.multiplier", 0.5);
    normalMultiplier = getConfig().getDouble("difficultySettings.Normal.multiplier", 1.0);
    hardMultiplier   = getConfig().getDouble("difficultySettings.Hard.multiplier", 1.5);

    easyPenalty   = getConfig().getInt("difficultySettings.Easy.penalty", 5);
    normalPenalty = getConfig().getInt("difficultySettings.Normal.penalty", 10);
    hardPenalty   = getConfig().getInt("difficultySettings.Hard.penalty", 15);

    treasureChestCounts.put("Easy",   getConfig().getInt("treasureChestCount.Easy", 3));
    treasureChestCounts.put("Normal", getConfig().getInt("treasureChestCount.Normal", 2));
    treasureChestCounts.put("Hard",   getConfig().getInt("treasureChestCount.Hard", 1));

    treasurePool.clear();
    for (String name : getConfig().getStringList("treasureItems")) {
      Material m = Material.matchMaterial(name);
      if (m == null) {
        try { m = Material.matchMaterial(name.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
      }
      if (m != null) treasurePool.add(m);
      else getLogger().warning("❌ 無効な Material: " + name);
    }

    if (treasurePool.isEmpty()) treasurePool.add(Material.DIAMOND);

    totalChests = getConfig().getInt("totalChests", 10);

    getLogger().info("✅ config.yml を読み込みました");
  }

  // =======================================================
  // ✅ ✅ ✅ 追加：Repository getter（QuoteFavoriteCommand用）
  // =======================================================
  public ProverbLogRepository getProverbLogRepository() {
    return proverbLogRepository;
  }

  // =======================================================
  // Commands
  // =======================================================
  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    // === AUTO PATCH v6.2: allow console/RCON ===
    Player player = null;
    if (sender instanceof Player) { player = (Player) sender; }
    else {
      if (args != null && args.length >= 2) player = Bukkit.getPlayerExact(args[1]);
      if (player == null) player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
      if (player == null) { sender.sendMessage(getI18n().tr(getConfig().getString("language.default", "en"), "finalAudit.command.playerOnlyConsoleNoOnline")); return true; }
    }
// === END AUTO PATCH v6.2 ===

    // ✅ treasureReload
    if (cmd.getName().equalsIgnoreCase("treasureReload")) {
      if (isRunning) {
        player.sendMessage(ChatColor.RED + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.reloadBlockedRunning"));
        return true;
      }

      // 1) config再読込
      reloadConfig();

      // ✅ messages.yml / config / allowedLanguages を再読込
      if (languageConfigStore != null) languageConfigStore.reloadFromConfig(getConfig());
      if (i18n != null) i18n.loadOrCreate();
      if (quoteModule != null) quoteModule.reload();

      // 2) 言語ストア再読込 + GUI作り直し（Listener再登録）
      initOrReloadLanguageSystem(true);

      // 3) このプラグイン内の設定値も読み直し
      loadConfigValues();

      // 4) manager系も必要なら作り直し（安全側）
      this.treasureChestManager =
          new TreasureChestManager(this, treasureChestCounts, treasurePool, chestSpawnRadius);

      // 近接サウンドも manager依存なので作り直す
      this.chestSound = new ChestProximitySoundService(
          this,
          this.treasureChestManager,
          () -> this.isRunning
      );

      // 旧GUIが開いてる人がいたら閉じる（古いlistener/slot対応が残らないように）
      for (Player p : Bukkit.getOnlinePlayers()) {
        try {
          if (p.getOpenInventory() != null && LANGUAGE_GUI_TITLE.equals(p.getOpenInventory().getTitle())) {
            p.closeInventory();
            p.sendMessage(ChatColor.YELLOW + getI18n().tr(getPlayerLangOrDefault(p.getUniqueId()), "finalAudit.command.guiReloadedReopen"));
          }
        } catch (Throwable ignored) {}
      }

      player.sendMessage(ChatColor.GREEN + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.treasureReloadDone"));
      return true;
    }

    if (cmd.getName().equalsIgnoreCase("gameRank")) {
      // /gameRank           -> weekly
      // /gameRank weekly    -> weekly
      // /gameRank all       -> all-time
      // /gameRank monthly   -> monthly
      String mode = (args.length >= 1) ? args[0].toLowerCase(Locale.ROOT) : "weekly";

      if (mode.equals("all") || mode.equals("alltime") || mode.equals("all-time")) {
        showAllTimeRanking(player);

      } else if (mode.equals("month") || mode.equals("monthly")) {
        showMonthlyRanking(player);

      } else {
        showWeeklyRanking(player);
      }

      rankDirty = true;
      return true;
    }

    // ✅ ✅ ✅ /gameMenu
// - /gameMenu      : チャット目次 + 本
// - /gameMenu gui  : 言語GUIを必ず開く（/gameStart gui のショートカット）
    if (cmd.getName().equalsIgnoreCase("gameMenu")) {

      // ✅ /gameMenu gui → 言語GUIを開いて、選んだ瞬間に本まで開く
      if (args != null && args.length >= 1 && args[0].equalsIgnoreCase("gui")) {
        if (languageSelectGui != null) {
          String diff = (difficulty == null || difficulty.isBlank()) ? "Normal" : difficulty;
          languageSelectGui.openForGameMenu(player, diff); // ✅ ここが変更点
        } else {
          player.sendMessage(ChatColor.RED + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.languageGuiNotReady"));
        }
        return true;
      }

      // ✅ 通常の /gameMenu
      final String lang = resolvePlayerLang(player);
      GameMenu.showGameMenu(player, difficulty, this, lang);
      GameMenu.openRuleBookFromConfig(player, difficulty, this, lang);
      return true;
    }

    // ✅ /gameStart /gamestart は「GUIを出すだけ」
    if (cmd.getName().equalsIgnoreCase("gameStart") || cmd.getName().equalsIgnoreCase("gamestart")) {

      if (isRunning) {
        player.sendMessage(ChatColor.RED + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.gameAlreadyRunning"));
        return true;
      }

      if (treasureRunGameEffectsPlugin != null) {
        treasureRunGameEffectsPlugin.stopFinalCountdownBeeps(player);
      }

      if (gameStageManager != null) {
        gameStageManager.clearDifficultyBlocks();
        gameStageManager.clearShopEntities();
      }
      if (treasureChestManager != null) treasureChestManager.removeAllChests();

      if (startThemePlayer != null) startThemePlayer.stop(player);
      stopVanillaMusicSuppress(player);

      if (heartbeatSoundService != null) heartbeatSoundService.stop(player);
      if (chestSound != null) chestSound.stop(player);

      // ✅✅✅ ここに追加：強制GUIオプション
      boolean forceGui = false;

      // 例: /gameStart gui normal
      if (args.length >= 1 && args[0].equalsIgnoreCase("gui")) {
        forceGui = true;

        // difficulty は次の引数へ（args を差し替える）
        args = (args.length >= 2) ? new String[]{ args[1] } : new String[0];
      }

      String selectedDifficulty;
      if (args.length >= 1) {
        String diff = args[0].toLowerCase(Locale.ROOT);
        if (diff.equals("easy") || diff.equals("normal") || diff.equals("hard")) {
          selectedDifficulty = diff.substring(0, 1).toUpperCase() + diff.substring(1);
        } else {
          player.sendMessage(ChatColor.RED + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.invalidDifficulty"));
          return true;
        }
      } else {
        selectedDifficulty = "Normal";
      }

      // ✅✅✅ 保存済み言語があるなら GUI を出さずに即開始（ただし forceGui の時はGUI優先）
      if (!forceGui) {
        String savedLang = null;
        try {
          if (playerLanguageStore != null) {
            // ✅ getLang() は locale を返す場合があるので「保存値(get)」だけを見る
            savedLang = playerLanguageStore.getLang(player, "");
          }
        } catch (Throwable ignored) {}

        if (savedLang != null && !savedLang.isBlank()) {
          beginGameStartAfterLanguageSelected(player, selectedDifficulty, savedLang);
          return true;
        }
      }

      // ✅ 未設定 or 強制GUI の時だけ GUI
      if (languageSelectGui != null) {
        languageSelectGui.open(player, selectedDifficulty);
      } else {
        player.sendMessage(ChatColor.RED + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.languageGuiNotReady"));
      }

      return true;
    }

    if (cmd.getName().equalsIgnoreCase("gameEnd")) {
      UUID uuid = player.getUniqueId();
      int score = playerScores.getOrDefault(uuid, 0);
      long elapsedSec = Math.max(0, (System.currentTimeMillis() - startTime) / 1000L);

      player.sendMessage(
          ChatColor.GOLD + trPlayer(
              player,
              "gameplay.pickup.gameEndTotalScore",
              I18n.Placeholder.of("{score}", String.valueOf(score))
          )
      );

      saveScore(player, score, elapsedSec, difficulty);

      if (treasureRunGameEffectsPlugin != null) treasureRunGameEffectsPlugin.stopFinalCountdownBeeps(player);
      if (heartbeatSoundService != null) heartbeatSoundService.stop(player);
      if (chestSound != null) chestSound.stop(player);

      if (startThemePlayer != null) startThemePlayer.stop(player);
      stopVanillaMusicSuppress(player);

      if (taskId != -1) {
        Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
      }

      if (finishTitleTaskId != -1) {
        Bukkit.getScheduler().cancelTask(finishTitleTaskId);
        finishTitleTaskId = -1;
      }

      if (timeUpTitleTaskId != -1) {
        Bukkit.getScheduler().cancelTask(timeUpTitleTaskId);
        timeUpTitleTaskId = -1;
      }

      if (gameStageManager != null) {
        gameStageManager.clearDifficultyBlocks();
        gameStageManager.clearShopEntities();
      }

      isRunning = false;
      if (bossBar != null) bossBar.removeAll();
      if (treasureChestManager != null) treasureChestManager.removeAllChests();
      playerScores.remove(uuid);

      // ✅ 手動終了は「TIME_UP連続」ではないのでリセット
      consecutiveTimeUpCounts.put(uuid, 0);

      restoreWorldAndPlayer(player);
      return true;
    }

    return false;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

    // ✅ 追加：/gameMenu gui のTab補完
    if (cmd.getName().equalsIgnoreCase("gameMenu")) {
      if (args.length == 1) {
        return Arrays.asList("gui");
      }
    }

    if (cmd.getName().equalsIgnoreCase("gameStart") || cmd.getName().equalsIgnoreCase("gamestart")) {
      if (args.length == 1) {
        return Arrays.asList("easy", "normal", "hard");
      }
    }
    return Collections.emptyList();
  }

  // /gamestart normal と /gamestart:normal 両対応
  @EventHandler
  public void onGamestartStyleCommand(PlayerCommandPreprocessEvent event) {
    String raw = event.getMessage().trim();
    String lower = raw.toLowerCase(Locale.ROOT);

    String mode;
    if (lower.startsWith("/gamestart:")) {
      mode = raw.substring("/gamestart:".length()).trim();
    } else if (lower.startsWith("/gamestart ")) {
      mode = raw.substring("/gamestart".length()).trim();
    } else {
      return;
    }

    mode = mode.toLowerCase(Locale.ROOT);
    if (!(mode.equals("easy") || mode.equals("normal") || mode.equals("hard"))) return;

    event.setCancelled(true);
    Player player = event.getPlayer();

    if (isRunning) {
      player.sendMessage(ChatColor.RED + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.gameAlreadyRunning"));
      return;
    }

    if (treasureRunGameEffectsPlugin != null) treasureRunGameEffectsPlugin.stopFinalCountdownBeeps(player);

    if (gameStageManager != null) {
      gameStageManager.clearDifficultyBlocks();
      gameStageManager.clearShopEntities();
    }
    if (treasureChestManager != null) treasureChestManager.removeAllChests();

    if (startThemePlayer != null) startThemePlayer.stop(player);
    stopVanillaMusicSuppress(player);

    if (heartbeatSoundService != null) heartbeatSoundService.stop(player);
    if (chestSound != null) chestSound.stop(player);

    String selectedDifficulty = mode.substring(0, 1).toUpperCase() + mode.substring(1);

    // ✅ 保存済み言語があるなら GUI を出さずに即開始（/lang en → /gamestart normal でも確定）
    String savedLang = null;
    try {
      if (playerLanguageStore != null) {
        savedLang = playerLanguageStore.getLang(player, "");
      }
    } catch (Throwable ignored) {}

    if (savedLang != null && !savedLang.isBlank()) {
      beginGameStartAfterLanguageSelected(player, selectedDifficulty, savedLang);
      return;
    }

    // ✅ 未設定の時だけ GUI
    if (languageSelectGui != null) {
      languageSelectGui.open(player, selectedDifficulty);
    } else {
      player.sendMessage(ChatColor.RED + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.languageGuiNotReady"));
    }
  }

  // =======================================================
  // ✅ 終了判定（最後の宝箱を開けた時）
  // =======================================================
  @EventHandler
  public void onInventoryOpen(InventoryOpenEvent event) {
    if (!isRunning) return;
    if (!(event.getPlayer() instanceof Player player)) return;
    if (treasureChestManager == null) return;

    Inventory inv = event.getInventory();
    Object holder = inv.getHolder();
    if (!(holder instanceof Chest) && !(holder instanceof DoubleChest)) return;

    Location chestLoc = inv.getLocation();
    if (chestLoc == null) return;
    Block chestBlock = chestLoc.getBlock();

    if (!treasureChestManager.isOurChest(chestBlock)) return;
    if (chestBlock.getType() != Material.CHEST) return;
    if (!isRunning) return;

    boolean hadAnyItem = false;
    boolean jackpot = false;

    if (chestBlock.getState() instanceof Chest chestState) {
      ItemStack[] contents = chestState.getBlockInventory().getContents();
      for (ItemStack item : contents) {
        if (item == null || item.getType() == Material.AIR) continue;

        hadAnyItem = true;

        String itemName = displayMaterialName(player, item.getType());
        player.sendMessage(
            ChatColor.GOLD + trPlayer(
                player,
                "gameplay.pickup.treasureFound",
                I18n.Placeholder.of("{name}", itemName),
                I18n.Placeholder.of("{amount}", String.valueOf(item.getAmount()))
            )
        );

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
        leftovers.values().forEach(remain ->
            player.getWorld().dropItemNaturally(player.getLocation(), remain)
        );

        switch (item.getType()) {
          case NETHERITE_INGOT, ENCHANTED_GOLDEN_APPLE, DIAMOND_BLOCK,
               EMERALD_BLOCK, GOLD_BLOCK, IRON_BLOCK -> jackpot = true;
          default -> {}
        }
      }

      chestState.getBlockInventory().clear();
      chestState.update();
    }

    if (hadAnyItem && startThemePlayer != null) {
      startThemePlayer.playTreasureSparkle(player);
    }

    int add = 0;
    if (hadAnyItem) add += 100;
    if (jackpot) add += 200;

    if (add > 0) {
      int newScore = playerScores.getOrDefault(player.getUniqueId(), 0) + add;
      playerScores.put(player.getUniqueId(), newScore);
      player.sendMessage(
          ChatColor.GREEN + trPlayer(
              player,
              "gameplay.pickup.scoreAdded",
              I18n.Placeholder.of("{add}", String.valueOf(add)),
              I18n.Placeholder.of("{total}", String.valueOf(newScore))
          )
      );
    }

    chestBlock.setType(Material.AIR);

    Location effectLoc = chestLoc.clone().add(0.5, 0.8, 0.5);
    World w = effectLoc.getWorld();
    if (w == null) return;

    w.spawnParticle(Particle.END_ROD, effectLoc, 40, 0.6, 0.6, 0.6, 0.02);
    spawnRisingPillars(effectLoc, Particle.END_ROD);
    burstStars(effectLoc);

    totalChestsRemaining = Math.max(0, totalChestsRemaining - 1);

    if (totalChestsRemaining > 0) {
      player.sendMessage(
          ChatColor.AQUA + trPlayer(
              player,
              "gameplay.pickup.remainingChests",
              I18n.Placeholder.of("{count}", String.valueOf(totalChestsRemaining))
          )
      );
      return;
    }

    if (!isRunning) return;
    isRunning = false;

    // ✅ SUCCESS（完走）したら「TIME_UP連続」はリセット
    consecutiveTimeUpCounts.put(player.getUniqueId(), 0);

    if (heartbeatSoundService != null) heartbeatSoundService.stop(player);
    if (chestSound != null) chestSound.stop(player);
    if (treasureRunGameEffectsPlugin != null) treasureRunGameEffectsPlugin.stopFinalCountdownBeeps(player);

    if (startThemePlayer != null) startThemePlayer.stop(player);
    stopVanillaMusicSuppress(player);

    if (gameStageManager != null) gameStageManager.clearDifficultyBlocks();

    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }

    if (finishTitleTaskId != -1) {
      Bukkit.getScheduler().cancelTask(finishTitleTaskId);
      finishTitleTaskId = -1;
    }

    if (timeUpTitleTaskId != -1) {
      Bukkit.getScheduler().cancelTask(timeUpTitleTaskId);
      timeUpTitleTaskId = -1;
    }

    final int finalScore = playerScores.getOrDefault(player.getUniqueId(), 0);

    long elapsedMs = System.currentTimeMillis() - startTime;
    long totalSeconds = elapsedMs / 1000;
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    long hundredths = (elapsedMs % 1000) / 10;

    final String timeText = String.format("%d:%02d.%02d", minutes, seconds, hundredths);
    final long elapsedSec = totalSeconds;

    saveScore(player, finalScore, elapsedSec, difficulty);

    // ✅ ここに追加（SUCCESS: weekly + alltime 加算）
    addSeasonScore(player, finalScore, true, elapsedMs);

    final int rank = getRunRank(player.getName(), finalScore, elapsedSec, difficulty);
    final String rankLabel = (rank > 0) ? ("#" + rank) : "-";

    // ✅ SUCCESS用：このRunで表示する哲学SUBTITLEを1回だけ決めて保持する（チカチカ防止）
    final String successLang = getPlayerLangOrDefault(player.getUniqueId());
    final String successPhiloSub = outcomeMessageService.sanitizeVisibleText(GameOutcome.SUCCESS, successLang, outcomeMessageService.pickSubtitle(GameOutcome.SUCCESS, difficulty, successLang));

    // =======================================================
    // ✅ ✅ ✅ 追加：SUCCESS の格言ログを MySQL に保存（proverb_logs）
    // =======================================================
    if (successPhiloSub != null && !successPhiloSub.isBlank()) {
      saveProverbLog(player.getUniqueId(), player.getName(), "SUCCESS", difficulty, successLang, successPhiloSub);
    }

    long djTotalTicksWork = (treasureRunGameEffectsPlugin != null)
        ? treasureRunGameEffectsPlugin.getDjTotalTicks()
        : 0L;

    if (djTotalTicksWork <= 0L) {
      djTotalTicksWork = getTotalEffectTicksForRank(rank);
    }

    final long djTotalTicksFinal = Math.max(0L, djTotalTicksWork);

    final long rewardStartDelay = 65L;
    final long finishDelay = rewardStartDelay + djTotalTicksFinal;

    Bukkit.getScheduler().runTaskLater(TreasureRunMultiChestPlugin.this, () -> {
      if (!player.isOnline()) return;

      if (rankRewardManager != null && rank > 0) {
        rankRewardManager.giveRankRewardWithEffect(player, rank, djTotalTicksFinal);
      }

      if (treasureRunGameEffectsPlugin != null) {
        treasureRunGameEffectsPlugin.triggerUltimateDJEvent(player, false);
      }
    }, rewardStartDelay);

    final long titleStartDelay = rewardStartDelay + 1;

    Bukkit.getScheduler().runTaskLater(TreasureRunMultiChestPlugin.this, () -> {
      if (!player.isOnline()) return;

      final long keepDuration = Math.max(0L, finishDelay - titleStartDelay);
      final long period = 10L;
      final long maxRuns = (keepDuration <= 0) ? 1 : (long) Math.ceil((double) keepDuration / period);

      finishTitleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
        long runs = 0;

        @Override
        public void run() {
          if (!player.isOnline()) {
            if (finishTitleTaskId != -1) {
              Bukkit.getScheduler().cancelTask(finishTitleTaskId);
              finishTitleTaskId = -1;
            }
            return;
          }

          String resultLang = getPlayerLangOrDefault(player.getUniqueId());

          String scoreLabel = getI18n().tr(resultLang, "gameplay.result.label.score");
          String timeLabel  = getI18n().tr(resultLang, "gameplay.result.label.time");
          String rankText   = getI18n().tr(resultLang, "gameplay.result.label.rank");

          String baseSub =
              ChatColor.GOLD + scoreLabel + ": " + finalScore +
                  ChatColor.YELLOW + "  " + timeLabel + ": " + timeText +
                  ChatColor.AQUA + "  " + rankText + ": " + ChatColor.LIGHT_PURPLE + rankLabel;

          String philoPart = (successPhiloSub == null || successPhiloSub.isBlank())
              ? ""
              : (ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "  — " + successPhiloSub);

          // ✅ DJ演出中ずっと「スコア行」だけを同じsubtitleで出し続ける（文言は出さない）
          player.sendTitle(
              ChatColor.AQUA + getI18n().tr(resultLang, "gameplay.result.runCompleteTitle"),
              baseSub,
              0, 20, 0
          );

          runs++;
          if (runs >= maxRuns) {
            if (finishTitleTaskId != -1) {
              Bukkit.getScheduler().cancelTask(finishTitleTaskId);
              finishTitleTaskId = -1;
            }
          }
        }
      }, 0L, period);

    }, titleStartDelay);

    final String playerName = player.getName();
    final UUID playerUuid = player.getUniqueId();

    Bukkit.getScheduler().runTaskLater(TreasureRunMultiChestPlugin.this, () -> {

      if (finishTitleTaskId != -1) {
        Bukkit.getScheduler().cancelTask(finishTitleTaskId);
        finishTitleTaskId = -1;
      }

      if (player.isOnline()) {
        player.sendMessage(ChatColor.GOLD + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "gameplay.success.allChestsOpened"));
        player.sendMessage(
            ChatColor.AQUA + getI18n().tr(
                getPlayerLangOrDefault(player.getUniqueId()),
                "gameplay.result.summaryLine",
                I18n.Placeholder.of("{time}", timeText),
                I18n.Placeholder.of("{score}", String.valueOf(finalScore)),
                I18n.Placeholder.of("{rank}", rankLabel)
            )
        );
        // ✅ ここでは哲学文を出さない（位置を下に移動するため）
      }

      if (gameStageManager != null) {
        gameStageManager.clearDifficultyBlocks();
        gameStageManager.clearShopEntities();
      }

      if (bossBar != null) bossBar.removeAll();
      if (treasureChestManager != null) treasureChestManager.removeAllChests();
      playerScores.remove(playerUuid);

      Bukkit.broadcastMessage(
          ChatColor.AQUA + getI18n().tr(
              getPlayerLangOrDefault(player.getUniqueId()),
              "gameplay.result.allChestsOpenedBroadcast",
              I18n.Placeholder.of("{player}", playerName),
              I18n.Placeholder.of("{score}", String.valueOf(finalScore))
          )
      );

      // ✅ ✅ ✅ 表示位置：
      // 「broadcastの後」→「1行空けた後」→「白字」で哲学文を表示
      // （= 画像の "The obstacle is the way." の位置）
      if (player.isOnline()) {
        if (successPhiloSub != null && !successPhiloSub.isBlank()) {

          // ✅ 1行空ける：空文字だと潰れることがあるので " " を送る
          player.sendMessage(" ");

          // 改行が入っている場合も想定して、行ごとに送る（白字で）
          String[] lines = successPhiloSub.split("\\R");
          for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            player.sendMessage(ChatColor.WHITE + trimmed); // ✅ ここだけ変更（WHITE → GRAY）
          }
        }
      }

      restoreWorldAndPlayer(player);

    }, finishDelay);
  }

  // =======================================================
  // ✅ Outcome ランダム哲学SUBTITLE表示（SUCCESS / TIME_UP）
  //  - 今回の完成版では:
  //    SUCCESSは finishTitleTaskId 側で “ずっと” 見せるため、ここは主にTIME_UP用として残す
  // =======================================================
  private void showOutcomeSubtitle(Player player, GameOutcome outcome) {
    String lang = getPlayerLangOrDefault(player.getUniqueId());
    String sub = outcomeMessageService.sanitizeVisibleText(outcome, lang, outcomeMessageService.pickSubtitle(outcome, difficulty, lang));
    if (sub == null || sub.isBlank()) return;

    String title = (outcome == GameOutcome.SUCCESS)
        ? getI18n().tr(lang, "gameplay.result.runCompleteTitle")
        : getI18n().tr(lang, "gameplay.result.timeUpTitle");
    ChatColor titleColor = (outcome == GameOutcome.SUCCESS) ? ChatColor.AQUA : ChatColor.RED;

    player.sendTitle(
        titleColor + title,
        ChatColor.GRAY + "" + ChatColor.ITALIC + sub,
        10, 60, 10
    );

    // 念のためチャットにも出す（不要なら削除OK）
    player.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + sub);
  }

  private void startGame(Player player) {
    getLogger().info("[StartTheme] startGame called by " + player.getName());

    isRunning = true;
    startTime = System.currentTimeMillis();

    if (heartbeatSoundService != null) {
      heartbeatSoundService.start(
          player,
          () -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000L;
            int timeLimit = switch (difficulty) {
              case "Easy" -> easyTimeLimit;
              case "Normal" -> normalTimeLimit;
              case "Hard"  -> hardTimeLimit;
              default      -> 180;
            };
            long remaining = Math.max(0L, (long) timeLimit - elapsed);
            return (int) Math.max(0L, remaining);
          },
          () -> this.isRunning
      );
    }

    if (startThemePlayer != null) startThemePlayer.startGameBgm(player);
    startVanillaMusicSuppress(player);

    if (chestSound != null) chestSound.start(player);

    World w = player.getWorld();
    previousWorldTime = w.getTime();
    previousStorm = w.hasStorm();
    previousThundering = w.isThundering();

    w.setTime(1000L);
    w.setStorm(false);
    w.setThundering(false);

    String initialLang = resolveCurrentLang(player);
    bossBar = Bukkit.createBossBar(
        ChatColor.GREEN + getI18n().tr(initialLang, "gameplay.timer.remainingBossbar"),
        BarColor.GREEN,
        BarStyle.SOLID
    );
    bossBar.addPlayer(player);
    bossBar.setTitle(ChatColor.GREEN + getI18n().tr(initialLang, "gameplay.timer.remainingBossbar"));

    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
      String langNow = resolveCurrentLang(player);

      long elapsed = (System.currentTimeMillis() - startTime) / 1000;
      int timeLimit = switch (difficulty) {
        case "Easy" -> easyTimeLimit;
        case "Normal" -> normalTimeLimit;
        case "Hard"  -> hardTimeLimit;
        default      -> 180;
      };

      long remaining = Math.max(0, timeLimit - elapsed);
      bossBar.setProgress((double) remaining / timeLimit);
      bossBar.setTitle(ChatColor.GREEN + getI18n().tr(langNow, "gameplay.timer.remainingBossbar"));

      player.spigot().sendMessage(
          ChatMessageType.ACTION_BAR,
          new TextComponent(
              ChatColor.YELLOW + getI18n().tr(
                  langNow,
                  "gameplay.timer.remainingLine",
                  I18n.Placeholder.of("{seconds}", String.valueOf(remaining))
              )
          )
      );

      if (remaining == 5) {
        if (treasureRunGameEffectsPlugin != null) {
          treasureRunGameEffectsPlugin.startFinalCountdownBeeps(player);
        }
      }

      if (remaining <= 0) {
        if (taskId != -1) {
          Bukkit.getScheduler().cancelTask(taskId);
          taskId = -1;
        }

        if (gameStageManager != null) {
          gameStageManager.clearDifficultyBlocks();
          gameStageManager.clearShopEntities();
        }

        // ✅ TIME_UP：哲学文（固定位置で最後に出すため、ここでは「保持」だけ）
        final String timeUpLang = resolveCurrentLang(player);
        final String timeUpPhiloSub = outcomeMessageService.sanitizeVisibleText(GameOutcome.TIME_UP, timeUpLang, outcomeMessageService.pickSubtitle(GameOutcome.TIME_UP, difficulty, timeUpLang));

        // ✅ TIME_UP：連続回数カウント（ここで +1）
        final UUID puid = player.getUniqueId();
        final int streak = consecutiveTimeUpCounts.getOrDefault(puid, 0) + 1;
        consecutiveTimeUpCounts.put(puid, streak);

        // ✅ ✅ ✅ Easy / Normal / Hard どれでも「3回連続TIME_UP」だけで出す（3回目で発火）
        // ★変更点：streak >= 3 ではなく「streak == 3」にする（“3回連続”を厳密に守る）
        final boolean showAdversityAfterScene = (streak == 3);

        // ✅ TIME_UP表示中のTitleは常に TIME'S UP!（ここは変えない）
        final String timeUpTitleText = getI18n().tr(timeUpLang, "gameplay.result.timeUpTitle");

        // ✅ TIME_UP：しばらく固定でTitle表示（SUCCESSみたいに上書きされない、見逃しにくい）
        // 表示時間: 4秒（80 ticks）/ 10 ticksごと更新 → 8回
        final long period = 10L;
        final long keepTicks = 80L;
        final long maxRuns = Math.max(1L, (long) Math.ceil((double) keepTicks / period));

        // 既にTIME_UPタスクが動いてたら止める
        if (timeUpTitleTaskId != -1) {
          Bukkit.getScheduler().cancelTask(timeUpTitleTaskId);
          timeUpTitleTaskId = -1;
        }

        final int got = Math.max(0, totalChestsAtStart - totalChestsRemaining);
        final String baseSub =
            ChatColor.GOLD + getI18n().tr(
                timeUpLang,
                "gameplay.result.gotLine",
                I18n.Placeholder.of("{got}", String.valueOf(got)),
                I18n.Placeholder.of("{total}", String.valueOf(totalChestsAtStart))
            );

        final String philoPart = (timeUpPhiloSub == null || timeUpPhiloSub.isBlank())
            ? ""
            : (ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "  — " + timeUpPhiloSub);

        timeUpTitleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
          long runs = 0;

          @Override
          public void run() {
            if (!player.isOnline()) {
              if (timeUpTitleTaskId != -1) {
                Bukkit.getScheduler().cancelTask(timeUpTitleTaskId);
                timeUpTitleTaskId = -1;
              }
              return;
            }

            player.sendTitle(
                ChatColor.RED + timeUpTitleText,
                baseSub,
                0, 20, 0
            );

            runs++;
            if (runs >= maxRuns) {
              if (timeUpTitleTaskId != -1) {
                Bukkit.getScheduler().cancelTask(timeUpTitleTaskId);
                timeUpTitleTaskId = -1;
              }
            }
          }
        }, 0L, period);

        // =======================================================
        // ✅ ✅ ✅ 追加：TIME_UP の格言ログを MySQL に保存（proverb_logs）
        // =======================================================
        if (timeUpPhiloSub != null && !timeUpPhiloSub.isBlank()) {
          saveProverbLog(player.getUniqueId(), player.getName(), "TIME_UP", difficulty, timeUpLang, timeUpPhiloSub);
        }

        String outcomeNoticeLang = getConfig().getString("language.default", "en");
        try {
          if (getPlayerLanguageStore() != null) {
            String saved = getPlayerLanguageStore().getLang(player, outcomeNoticeLang);
            if (saved != null && !saved.isBlank()) outcomeNoticeLang = saved;
          }
        } catch (Throwable ignored) {}

        player.sendMessage(ChatColor.WHITE + getI18n().tr(outcomeNoticeLang, "outcome.notice.timeUpGameOver"));
        player.sendMessage(ChatColor.WHITE + getI18n().tr(outcomeNoticeLang, "outcome.notice.retryGuide"));

        if (treasureRunGameEffectsPlugin != null) {
          treasureRunGameEffectsPlugin.playTimeUpFailCue(player, got, totalChestsAtStart);
        }

        int finalScore = playerScores.getOrDefault(player.getUniqueId(), 0);
        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000L;
        saveScore(player, finalScore, elapsedSec, difficulty);

        // ✅ ここに追加（TIME_UP: scoreだけ加算 / wins・best_timeは無し）
        addSeasonScore(player, finalScore, false, null);

        if (startThemePlayer != null) startThemePlayer.stop(player);
        stopVanillaMusicSuppress(player);

        if (heartbeatSoundService != null) heartbeatSoundService.stop(player);

        if (treasureRunGameEffectsPlugin != null) {
          treasureRunGameEffectsPlugin.stopFinalCountdownBeeps(player);
        }

        if (chestSound != null) chestSound.stop(player);

        isRunning = false;
        if (bossBar != null) bossBar.removeAll();
        if (treasureChestManager != null) treasureChestManager.removeAllChests();
        playerScores.remove(player.getUniqueId());

        // ✅ ✅ ✅ TIME_UP の表示順（完全版）：
        // 1) Title に Got: x/y を固定で見せる（keepTicks）
        // 2) 画面切替（restoreWorldAndPlayer）
        // 3) （切替後）チャットに英語メッセージ
        // 4) 1行空けて名言（グレー）
        // 5) ✅ 3回連続の時だけ：場面切替後に “サブタイトルの表示場所” に水色で OVERCOMING ADVERSITY を出す
        final String outcomeNoticeLangFinal = outcomeNoticeLang;
        Bukkit.getScheduler().runTaskLater(TreasureRunMultiChestPlugin.this, () -> {
          if (!player.isOnline()) return;

          // ✅ 画面切替直前：日本語案内が次画面で上のほうに残りにくいように「空行で押し上げる」
          // （チャットを消さず、追加で流すだけ）
          for (int i = 0; i < 6; i++) {
            player.sendMessage(" ");
          }

          // ✅ 画面切替を先に行う（次の画面で英語が来るようにする）
          restoreWorldAndPlayer(player);

          // ✅ ✅ ✅ 3回連続TIME_UPのときだけ：場面切替後に “サブタイトル位置” へ表示（TreasureRun TOPと同系の白寄り水色）
          if (showAdversityAfterScene) {
            String advColor;
            try {
              // TreasureRun TOP に近い #55FFFF（AQUA相当の明るい水色）
              advColor = net.md_5.bungee.api.ChatColor.of("#55FFFF").toString();
            } catch (Throwable t) {
              advColor = ChatColor.AQUA.toString(); // ✅ フォールバック
            }

            // 直前のTitle表示を確実に消してから上書き
            player.sendTitle("", "", 0, 1, 0);

            // ✅ ✅ ✅ サブタイトルの表示箇所へ出す（タイトル行は空）
            // ★変更点：表示時間を長めにする（チャットが消える1.5秒前くらいまでを想定）
            // 例：stay=170 ticks(8.5秒) + fadeOut=10 ticks(0.5秒) → 合計約9秒
            player.sendTitle(
                " ",
                advColor + getI18n().tr(outcomeNoticeLangFinal, "gameplay.result.overcomingAdversity"),
                0, 170, 10
            );

            // ✅ ここで連続カウントをリセット（次にまた3回連続で出す）
            consecutiveTimeUpCounts.put(puid, 0);
          }

          // retryGuide is already sent once above with timeUpGameOver.
          // Do not send it again here, otherwise the same line appears twice in chat.

          // ✅ 名言：1行空けて、グレーで最下段へ（ここ以外は一切いじらない）
          if (timeUpPhiloSub != null && !timeUpPhiloSub.isBlank()) {
            player.sendMessage(" ");

            String[] lines = timeUpPhiloSub.split("\\R");
            for (String line : lines) {
              if (line == null) continue;
              String trimmed = line.trim();
              if (trimmed.isEmpty()) continue;

              // ✅ 変更点：格言の色を「Try a different route...」と同じグレーに統一
              player.sendMessage(ChatColor.GRAY + trimmed);
            }
          }

        }, keepTicks + 2L);
      }

    }, 0L, 20L);
  }

  public boolean isGameRunning() {
    return isRunning;
  }

  private void restoreWorldAndPlayer(Player player) {
    UUID uuid = player.getUniqueId();

    Location original = originalLocations.remove(uuid);
    if (original != null && player.isOnline()) {
      player.teleport(original);
    }

    if (previousWorldTime >= 0) {
      World w = player.getWorld();
      w.setTime(previousWorldTime);
      w.setStorm(previousStorm);
      w.setThundering(previousThundering);
      previousWorldTime = -1;
    }
  }

  private void spawnRisingPillars(Location center, Particle particle) {
    World w = center.getWorld();
    if (w == null) return;

    new BukkitRunnable() {
      double yOff = 0;

      @Override
      public void run() {
        for (int i = -1; i <= 1; i++) {
          for (int j = -1; j <= 1; j++) {
            Location loc = center.clone().add(i * 0.5, yOff, j * 0.5);
            w.spawnParticle(particle, loc, 3, 0.05, 0.1, 0.05, 0.01);
          }
        }
        yOff += 0.25;
        if (yOff > 3.5) cancel();
      }

    }.runTaskTimer(this, 0L, 2L);
  }

  public void burstStars(Location center) {
    World w = center.getWorld();
    if (w == null) return;

    for (int i = 0; i < 60; i++) {
      double angle = Math.random() * Math.PI * 2;
      double speed = 0.2 + Math.random() * 0.3;
      double vx = Math.cos(angle) * speed;
      double vz = Math.sin(angle) * speed;
      double vy = 0.2 + Math.random() * 0.3;

      w.spawnParticle(Particle.END_ROD,
          center.clone().add(0, 1.2, 0),
          0, vx, vy, vz, 1);
    }
  }

  public GameStageManager getGameStageManager() { return gameStageManager; }
  public TreasureItemFactory getItemFactory() { return itemFactory; }
  public TreasureChestManager getTreasureChestManager() { return treasureChestManager; }
  public RankRewardManager getRankRewardManager() { return rankRewardManager; }
  public TreasureRunGameEffectsPlugin getTreasureRunGameEffectsPlugin() { return treasureRunGameEffectsPlugin; }

  // ✅ 追加（RankTickerが参照する）
  public SeasonRepository getSeasonRepository() { return seasonRepository; }
  public SeasonScoreRepository getSeasonScoreRepository() { return seasonScoreRepository; }

  private int getTotalEffectTicksForRank(int rank) {
    return switch (rank) {
      case 1 -> 180;
      case 2 -> 140;
      case 3 -> 50;
      default -> 0;
    };
  }

  // =======================================================
  // ✅ バニラMinecraft BGM(MUSICカテゴリ)抑制ロジック
  // =======================================================
  public void startVanillaMusicSuppress(Player player) {
    stopVanillaMusicSuppress(player);

    BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
      if (!player.isOnline() || !isRunning) {
        stopVanillaMusicSuppress(player);
        return;
      }
      suppressVanillaMusicOnce(player);
    }, 0L, 100L);

    vanillaMusicSuppressTasks.put(player.getUniqueId(), task);
  }

  private void stopVanillaMusicSuppress(Player player) {
    BukkitTask task = vanillaMusicSuppressTasks.remove(player.getUniqueId());
    if (task != null) task.cancel();
  }

  private void stopVanillaMusicSuppressAll() {
    for (BukkitTask task : vanillaMusicSuppressTasks.values()) {
      task.cancel();
    }
    vanillaMusicSuppressTasks.clear();
  }

  private void suppressVanillaMusicOnce(Player player) {
    player.stopSound(SoundCategory.MUSIC);
    player.stopSound(SoundCategory.AMBIENT);

    for (Sound s : Sound.values()) {
      String name = s.name();
      if (name.startsWith("MUSIC_")) {
        player.stopSound(s, SoundCategory.MUSIC);
      }
    }
  }

  // ✅ /gameMenu gui 用：ゲーム開始せず「その言語で本だけ開く」
  public void openGameMenuOnly(Player player, String lang) {
    if (player == null) return;

    String actualLang = lang;
    if (actualLang == null || actualLang.isBlank()) {
      actualLang = getConfig().getString("language.default", "ja");
    }

    // 永続保存（/lang と同じ扱い）
    try {
      if (playerLanguageStore != null) {
        playerLanguageStore.set(player.getUniqueId(), actualLang);
      }
    } catch (Throwable ignored) {}

    // このRun用の一時保持も更新（格言ログなどに使う）
    try {
      playerLastLang.put(player.getUniqueId(), actualLang);
    } catch (Throwable ignored) {}

    GameMenu.showGameMenu(player, difficulty, this, actualLang);
    GameMenu.openRuleBookFromConfig(player, difficulty, this, actualLang);
  }

  // =======================================================
  // ✅ 言語選択後の新フロー開始
  // =======================================================
  public void beginGameStartAfterLanguageSelected(Player player, String selectedDifficulty, String lang) {

    if (isRunning) {
      player.sendMessage(ChatColor.RED + getI18n().tr(getPlayerLangOrDefault(player.getUniqueId()), "finalAudit.command.gameAlreadyRunning"));
      return;
    }

    if (treasureRunGameEffectsPlugin != null) {
      treasureRunGameEffectsPlugin.stopFinalCountdownBeeps(player);
    }

    if (gameStageManager != null) {
      gameStageManager.clearDifficultyBlocks();
      gameStageManager.clearShopEntities();
    }
    if (treasureChestManager != null) treasureChestManager.removeAllChests();

    if (startThemePlayer != null) startThemePlayer.stop(player);
    stopVanillaMusicSuppress(player);

    if (heartbeatSoundService != null) heartbeatSoundService.stop(player);
    if (chestSound != null) chestSound.stop(player);

    // 念のため（前回TIME_UPの固定表示が残ってたら止める）
    if (timeUpTitleTaskId != -1) {
      Bukkit.getScheduler().cancelTask(timeUpTitleTaskId);
      timeUpTitleTaskId = -1;
    }

    playerScores.put(player.getUniqueId(), 0);

    if (treasureRunGameEffectsPlugin != null) {
      for (Player p : Bukkit.getOnlinePlayers()) {
        treasureRunGameEffectsPlugin.resetPlayerTreasureCount(p);
      }
    }

    if (selectedDifficulty == null || selectedDifficulty.isBlank()) {
      difficulty = "Normal";
    } else {
      difficulty = selectedDifficulty;
    }

    // =======================================================
    // ✅ ✅ ✅ 追加：このRunで選んだ言語を保持（格言ログ保存に使う）
    // =======================================================
    if (lang == null || lang.isBlank()) {
      lang = getConfig().getString("language.default", "ja");
    }
    playerLastLang.put(player.getUniqueId(), lang);

    // ✅ ✅ ✅ 追加：Favorites図鑑側が参照する正式保存（永続）
    if (playerLanguageStore != null) {
      playerLanguageStore.set(player.getUniqueId(), lang);
    }

    originalLocations.put(player.getUniqueId(), player.getLocation().clone());

    Location stage = gameStageManager.buildSeasideStageAndTeleport(player);
    currentStageCenter = stage;

    int currentTotalChests = totalChests;
    treasureChestManager.spawnChests(player, difficulty, currentTotalChests);
    totalChestsRemaining = currentTotalChests;
    totalChestsAtStart = currentTotalChests;
    TreasureChestManager m = getTreasureChestManager();
    getLogger().info("[CHEST][SPAWN] after spawn"
        + " now=" + (m != null ? m.getTreasureLocations().size() : -1)
        + " instance=" + (m != null ? System.identityHashCode(m) : -1)
    );

    player.sendMessage(ChatColor.GREEN + trPlayer(player, "gameplay.setup.chestsPlaced", I18n.Placeholder.of("{count}", String.valueOf(currentTotalChests))));

    GameMenu.showGameMenu(player, difficulty, this, lang);
    GameMenu.openRuleBookFromConfig(player, difficulty, this, lang);

    new BukkitRunnable() {
      @Override
      public void run() {
        new BukkitRunnable() {
          int count = 3;

          @Override
          public void run() {
            if (count > 0) {

              // ✅ ✅ ✅ 数字の色切り替え（3=水色、2=翠、1=黄色）
              ChatColor numColor = switch (count) {
                case 3 -> ChatColor.AQUA;
                case 2 -> ChatColor.GREEN;
                case 1 -> ChatColor.YELLOW;
                default -> ChatColor.GRAY;
              };

              // ✅ ✅ ✅ 最大サイズで見せる：Title行に数字だけ出す（これが一番デカい）
              // Subtitleは「白に一番近いグレー」
              String startLang = getPlayerLangOrDefault(player.getUniqueId());

              player.sendTitle(
                  numColor + "" + ChatColor.BOLD + count,
                  ChatColor.GRAY + getI18n().tr(startLang, "gameplay.start.startingIn"),
                  0, 20, 0
              );

              // ✅ 追加（3/2/1の時）
              startThemePlayer.playCountdownTick(player, count);

              count--;
            } else {

              // ✅ 追加（GO!の時）
              startThemePlayer.playGoActivate(player);

              // ✅ ✅ ✅ GO! の瞬間だけ「バブル→震え + 光 + 爆発風スパークル」
              Bukkit.getScheduler().runTaskLater(TreasureRunMultiChestPlugin.this, () -> {
                if (player != null && player.isOnline()) {
                  playGoBubbleBurst(player);   // ✅ NEW：バブルが弾ける
                  playGoSparkleShock(player);  // ✅ 既存：震え + 光 + スパークル
                }
              }, 1L);
// ✅ ✅ ✅ 最大サイズで見せる：Title行に GO! だけ（太字）
              // ✅ 色は「白に一番近いグレー」
              String startLang = getPlayerLangOrDefault(player.getUniqueId());

              player.sendTitle(
                  ChatColor.GRAY + "" + ChatColor.BOLD + getI18n().tr(startLang, "gameplay.start.go"),
                  "",
                  0, 20, 10
              );

              TreasureRunMultiChestPlugin.this.startGame(player);
              this.cancel();
            }
          }

        }.runTaskTimer(TreasureRunMultiChestPlugin.this, 0L, 20L);
      }
    }.runTaskLater(this, 20L);
  }

  // =======================================================
  // ✅ ✅ ✅ GO! の瞬間だけ「震え + 光 + 爆発風スパークル」演出
  // - 物理的な揺れ（小さなテレポート揺らぎ）で疑似画面シェイク
  // - FLASH/火花/END_ROD + 爆発系SE
  // - 視覚だけの雷（strikeLightningEffect）
  // =======================================================
  private void playGoSparkleShock(Player player) {
    if (player == null || !player.isOnline()) return;

    Location base = player.getLocation().clone();
    World w = base.getWorld();
    if (w == null) return;

    // ✅ 光：フラッシュ（白い閃光）
    try {
      w.spawnParticle(Particle.FLASH, base.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);
    } catch (Throwable ignored) {}

    // ✅ 火花：スパーク（周囲に散る）
    try {
      w.spawnParticle(Particle.FIREWORKS_SPARK, base.clone().add(0, 1.0, 0),
          90, 0.6, 0.6, 0.6, 0.25);
    } catch (Throwable ignored) {}

    // ✅ キラキラ：END_ROD（TreasureRunらしさ）
    try {
      w.spawnParticle(Particle.END_ROD, base.clone().add(0, 1.1, 0),
          45, 0.45, 0.55, 0.45, 0.03);
    } catch (Throwable ignored) {}

    // ✅ 爆発風：ちょっとだけ煙っぽい演出（控えめ）
    try {
      w.spawnParticle(Particle.SMOKE_LARGE, base.clone().add(0, 0.9, 0),
          14, 0.25, 0.18, 0.25, 0.01);
    } catch (Throwable ignored) {}

    // ✅ 音：爆発 + 花火（GO!の勢い）
    w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.65f, 1.55f);
    w.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.7f, 1.35f);
    w.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.8f, 1.6f);

    // ✅ 見た目だけの雷（ダメージなし）
    try {
      w.strikeLightningEffect(base);
    } catch (Throwable ignored) {}

    // ✅ 疑似画面シェイク（短く・安全に）
    // - 6tickだけ微小な揺れ
    final Location origin = base.clone();
    final Random rnd = new Random();

    new BukkitRunnable() {
      int ticks = 0;

      @Override
      public void run() {
        if (!player.isOnline()) {
          cancel();
          return;
        }

        // ✅ 最後は元位置に戻して終了
        if (ticks >= 6) {
          try {
            player.teleport(origin);
          } catch (Throwable ignored) {}
          cancel();
          return;
        }

        double dx = (rnd.nextDouble() - 0.5) * 0.18; // 揺れ幅（x）
        double dz = (rnd.nextDouble() - 0.5) * 0.18; // 揺れ幅（z）

        Location shaken = origin.clone().add(dx, 0.0, dz);

        // yaw/pitch も微妙に振って「カメラ揺れ感」を作る
        float yaw = origin.getYaw() + (float) ((rnd.nextDouble() - 0.5) * 8.0);
        float pitch = origin.getPitch() + (float) ((rnd.nextDouble() - 0.5) * 6.0);
        shaken.setYaw(yaw);
        shaken.setPitch(pitch);

        try {
          player.teleport(shaken);
        } catch (Throwable ignored) {}

        ticks++;
      }

    }.runTaskTimer(this, 0L, 1L);
  }

  // =======================================================
  // ✅ NEW：GO! の瞬間に「バブルが弾ける」演出
  // - 空中でも見える粒子（BUBBLE_POP中心）
  // - 1回の破裂＋短い上昇バブルで“弾けた感”を作る
  // =======================================================
  private void playGoBubbleBurst(Player player) {
    if (player == null || !player.isOnline()) return;

    Location base = player.getLocation().clone();
    World w = base.getWorld();
    if (w == null) return;

    Location p = base.clone().add(0, 1.0, 0); // 胸〜顔あたりで弾ける

    // ✅ 音（バブルの破裂 + 軽い水しぶき）
    try {
      w.playSound(p, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.PLAYERS, 0.9f, 1.6f);
    } catch (Throwable ignored) {}
    try {
      w.playSound(p, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 0.35f, 1.9f);
    } catch (Throwable ignored) {}

    // ✅ “破裂”の中心（バブルが弾ける）
    try {
      w.spawnParticle(Particle.BUBBLE_POP, p, 80, 0.55, 0.35, 0.55, 0.02);
    } catch (Throwable ignored) {}

    // ✅ しぶき（軽く）
    try {
      w.spawnParticle(Particle.WATER_SPLASH, p, 18, 0.35, 0.18, 0.35, 0.02);
    } catch (Throwable ignored) {}

    // ✅ 上に抜けるバブル（短い余韻）
    new BukkitRunnable() {
      int t = 0;
      @Override
      public void run() {
        if (!player.isOnline()) { cancel(); return; }

        Location up = p.clone().add(0, t * 0.12, 0);

        try {
          w.spawnParticle(Particle.BUBBLE_COLUMN_UP, up, 30, 0.28, 0.18, 0.28, 0.02);
        } catch (Throwable ignored) {}

        t++;
        if (t >= 6) cancel(); // 約0.3秒
      }
    }.runTaskTimer(TreasureRunMultiChestPlugin.this, 0L, 1L);
  }

  // =======================================================
  // ✅ Weekly + All-time スコア加算（共通）
  // - SUCCESS / TIME_UP の両方から呼べる
  // =======================================================
  private void addSeasonScore(
      Player player,
      int addScore,
      boolean isWin,
      Long bestTimeMsOrNull
  ) {
    if (player == null) return;
    if (seasonRepository == null || seasonScoreRepository == null) {
      getLogger().warning("[RANK] season repos not initialized");
      return;
    }

    try {
      long seasonId = seasonRepository.getOrCreateCurrentWeeklySeasonId();

      // ✅ 追加：プレイヤー言語（無ければ ja）
      String langCode = "ja";
      try {
        if (playerLanguageStore != null) {
          langCode = playerLanguageStore.getLang(player, "ja");
        }
      } catch (Throwable ignored) {}

      seasonScoreRepository.addWeeklyAndAllTime(
          seasonId,
          player.getUniqueId(),
          player.getName(),
          addScore,
          isWin ? 1 : 0,
          bestTimeMsOrNull,
          langCode
      );

      rankDirty = true;

    } catch (Exception e) {
      getLogger().warning("[RANK] addSeasonScore failed: " + e.getMessage());
      e.printStackTrace();
    }
  }

  // =======================================================
  // ✅ ✅ ✅ 追加：proverb_logs 保存（MySQL）
  // - SUCCESS / TIME_UP のときの格言ログを保存する
  // =======================================================
  public void saveProverbLog(UUID playerUuid, String playerName,
      String outcome, String difficulty,
      String lang, String quoteText) {

    if (playerUuid == null) return;
    if (playerName == null) playerName = "unknown";
    if (outcome == null || outcome.isBlank()) outcome = "UNKNOWN";
    if (difficulty == null || difficulty.isBlank()) difficulty = "Normal";
    if (lang == null || lang.isBlank()) lang = getConfig().getString("language.default", "ja");
    if (quoteText == null || quoteText.isBlank()) return;

    String sql =
        "INSERT INTO proverb_logs (player_uuid, player_name, outcome, difficulty, lang, quote_text) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
      ps.setString(1, playerUuid.toString());
      ps.setString(2, playerName);
      ps.setString(3, outcome);
      ps.setString(4, difficulty);
      ps.setString(5, lang);
      ps.setString(6, quoteText);
      ps.executeUpdate();

    } catch (SQLException e) {
      getLogger().warning("⚠ proverb_logs 保存失敗: " + e.getMessage());
      e.printStackTrace();
    }
  }

  // =======================================================
  // ✅ 格言ログ取得（MySQL）
  // - プレイヤーごとの最新ログを返す
  // =======================================================
  public List<String> getRecentProverbs(UUID playerUuid, int limit) {
    List<String> list = new ArrayList<>();
    if (playerUuid == null) return list;

    // ✅ ✅ ✅ ここ追加（安全）
    Connection conn = getConnection();
    if (conn == null) return list;

    String sql =
        "SELECT outcome, difficulty, lang, quote_text, created_at " +
            "FROM proverb_logs " +
            "WHERE player_uuid = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT ?";

    // ✅ conn を使う
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, playerUuid.toString());
      ps.setInt(2, Math.max(1, limit));

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String outcome = rs.getString("outcome");
          String diff = rs.getString("difficulty");
          String lang = rs.getString("lang");
          String quote = rs.getString("quote_text");

          // ✅ 1行表示用フォーマット（本のページで見やすい）
          String row = "【" + outcome + " / " + diff + " / " + lang + "】\n" + quote;
          list.add(row);
        }
      }

    } catch (SQLException e) {
      getLogger().warning("⚠ proverb_logs 取得失敗: " + e.getMessage());
      e.printStackTrace();
    }

    return list;
  }

  // =======================================================
  // ✅ 現在のプレイヤー言語を解決（ゲーム中 /lang 切替に追従）
  // 優先順位：
  //  0) PlayerLanguageStore（/lang の保存先）
  //  1) LanguageStore（GUI由来）
  //  2) playerLastLang（ゲーム開始時に保存した言語）
  //  3) config の language.default
  // =======================================================
  private String resolveCurrentLang(Player player) {
    String defaultLang = getConfig().getString("language.default", "ja");
    if (player == null) return defaultLang;

    try {
      if (playerLanguageStore != null) {
        String saved = playerLanguageStore.getLang(player, "");
        if (saved != null && !saved.isBlank()) return saved;
      }
    } catch (Throwable ignored) {}

    UUID uuid = player.getUniqueId();

    String langFromStore = tryGetLangFromLanguageStore(uuid);
    if (langFromStore != null && !langFromStore.isBlank()) {
      return langFromStore;
    }

    String fromLast = playerLastLang.get(uuid);
    if (fromLast != null && !fromLast.isBlank()) {
      return fromLast;
    }

    return defaultLang;
  }

  // =======================================================
  // ✅ UUID から言語取得（オンライン中は“現在言語”を優先）
  // =======================================================
  private String getPlayerLangOrDefault(UUID uuid) {
    String defaultLang = getConfig().getString("language.default", "ja");
    if (uuid == null) return defaultLang;

    try {
      Player online = Bukkit.getPlayer(uuid);
      if (online != null) {
        return resolveCurrentLang(online);
      }
    } catch (Throwable ignored) {}

    String v = playerLastLang.get(uuid);
    if (v != null && !v.isBlank()) return v;

    return defaultLang;
  }

  // =======================================================
  // ✅ /gameMenu 用に「今のプレイヤー言語」を解決する
  // =======================================================
  private String resolvePlayerLang(Player player) {
    return resolveCurrentLang(player);
  }

  // =======================================================
  // ✅ 追加：LanguageStore から言語を “コンパイルエラー無し” で取得する（反射）
  // - LanguageStore 側のメソッド名が何であっても対応できるようにする
  // - 見つからない場合は null を返す
  // =======================================================
  private String tryGetLangFromLanguageStore(UUID uuid) {
    if (uuid == null) return null;
    if (languageStore == null) return null;

    // ありそうなメソッド名候補（あなたのLanguageStore実装に合わせて自動で拾う）
    String[] candidates = new String[]{
        "getPlayerLanguage",
        "getSelectedLanguage",
        "getLang",
        "getLanguage",
        "getPlayerLang"
    };

    for (String methodName : candidates) {
      try {
        java.lang.reflect.Method m = languageStore.getClass().getMethod(methodName, UUID.class);
        Object ret = m.invoke(languageStore, uuid);
        if (ret instanceof String s) {
          if (!s.isBlank()) return s;
        }
      } catch (NoSuchMethodException ignore) {
        // 次の候補へ
      } catch (Throwable t) {
        // 何か例外が出ても落とさない
        getLogger().fine("LanguageStore reflection failed: " + methodName + " (" + t.getMessage() + ")");
      }
    }

    return null;
  }
}
