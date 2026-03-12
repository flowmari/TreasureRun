package plugin.quote;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import plugin.I18n;
import plugin.TreasureRunMultiChestPlugin;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class QuoteFavoriteCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;
  private final QuoteFavoritesBookBuilder bookBuilder;
  private final QuoteRereadService rereadService;
  private final I18n i18n;

  public QuoteFavoriteCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;

    // ✅ pluginが持っている i18n を “同じ参照” で拾う（/treasureReload で即反映させるため）
    this.i18n = resolveI18n(plugin);

    // ✅ Favorites図鑑Builder は I18n を渡して統一（壊れない）
    this.bookBuilder = new QuoteFavoritesBookBuilder(this.i18n);

    // ✅ reread は既存通り
    this.rereadService = new QuoteRereadService(plugin);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage("Players only.");
      return true;
    }

    UUID uuid = player.getUniqueId();

    Connection conn = null;
    try {
      conn = plugin.getMySQLConnection();
    } catch (Throwable ignored) {}

    if (args.length == 0) {
      showHelp(player);
      return true;
    }

    String sub = args[0].toLowerCase(Locale.ROOT);

    // ---------------------------------------------------
    // help
    // ---------------------------------------------------
    if (sub.equals("help")) {
      showHelp(player);
      return true;
    }

    // ---------------------------------------------------
    // latest
    // ---------------------------------------------------
    if (sub.equals("latest")) {
      if (conn == null || plugin.getProverbLogRepository() == null) {
        player.sendMessage(ChatColor.RED + "MySQL / Repository not ready.");
        return true;
      }

      boolean ok = plugin.getProverbLogRepository().favoriteLatestLog(conn, uuid);

      if (ok) {
        player.sendMessage(ChatColor.GREEN + "★ Favorite saved!");
      } else {
        player.sendMessage(ChatColor.RED + "Favorite not saved. (maybe no logs yet / or duplicate)");
      }
      return true;
    }

    // ---------------------------------------------------
    // list
    // ---------------------------------------------------
    if (sub.equals("list")) {
      if (conn == null || plugin.getProverbLogRepository() == null) {
        player.sendMessage(ChatColor.RED + "MySQL / Repository not ready.");
        return true;
      }

      List<String> favs = plugin.getProverbLogRepository().loadFavorites(conn, uuid, 20);

      player.sendMessage(ChatColor.AQUA + "★ Favorites (latest 20): " + ChatColor.GRAY + "(count=" + favs.size() + ")");
      for (String row : favs) {
        player.sendMessage(ChatColor.GRAY + "------------------------------");
        for (String line : row.split("\n")) {
          player.sendMessage(ChatColor.WHITE + line);
        }
      }
      return true;
    }

    // ---------------------------------------------------
    // remove <id>
    // ---------------------------------------------------
    if (sub.equals("remove")) {
      if (args.length < 2) {
        player.sendMessage(ChatColor.RED + "Usage: /quoteFavorite remove <id>");
        return true;
      }

      if (conn == null || plugin.getProverbLogRepository() == null) {
        player.sendMessage(ChatColor.RED + "MySQL / Repository not ready.");
        return true;
      }

      int id;
      try {
        id = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        player.sendMessage(ChatColor.RED + "ID must be a number.");
        return true;
      }

      boolean ok = plugin.getProverbLogRepository().deleteFavoriteById(conn, uuid, id);

      if (ok) {
        player.sendMessage(ChatColor.GREEN + "★ Favorite removed: #" + id);
      } else {
        player.sendMessage(ChatColor.RED + "Favorite not removed. (not found?)");
      }
      return true;
    }

    // ---------------------------------------------------
    // reread [chat|title|book]
    // ---------------------------------------------------
    if (sub.equals("reread")) {
      String mode = (args.length >= 2) ? args[1].toLowerCase(Locale.ROOT) : "chat";

      QuoteRereadService.OutputMode outMode = QuoteRereadService.OutputMode.CHAT;
      if (mode.equals("title")) outMode = QuoteRereadService.OutputMode.TITLE;
      if (mode.equals("book")) outMode = QuoteRereadService.OutputMode.BOOK;

      boolean ok = rereadService.rereadRandom(player, outMode);
      if (!ok) {
        player.sendMessage(ChatColor.YELLOW + "★ No quotes yet.");
      }
      return true;
    }

    // ---------------------------------------------------
    // book [toc|success|timeup|other|full]
    // ---------------------------------------------------
    if (sub.equals("book")) {
      String mode = (args.length >= 2) ? args[1].toLowerCase(Locale.ROOT) : "full";

      // ✅ プレイヤー言語（PlayerLanguageStoreがある前提）
      String lang = resolvePlayerLang(player);

      // ✅ お気に入り取得
      List<String> rawRows = new ArrayList<>();
      try {
        if (conn != null && plugin.getProverbLogRepository() != null) {
          rawRows = plugin.getProverbLogRepository().loadFavorites(conn, uuid, 200);
        }
      } catch (Throwable ignored) {}

      // ✅ repositoryは List<String> を返すので、図鑑Builderが読める形に変換する
      List<Object> rows = rawRows.stream()
          .map(QuoteFavoriteCommand::toRowObject)
          .collect(Collectors.toList());

      QuoteFavoritesBookBuilder.ViewMode view = QuoteFavoritesBookBuilder.ViewMode.FULL;
      if (mode.equals("toc")) view = QuoteFavoritesBookBuilder.ViewMode.TOC_ONLY;
      if (mode.equals("success")) view = QuoteFavoritesBookBuilder.ViewMode.SUCCESS_ONLY;
      if (mode.equals("timeup")) view = QuoteFavoritesBookBuilder.ViewMode.TIME_UP_ONLY;
      if (mode.equals("other")) view = QuoteFavoritesBookBuilder.ViewMode.OTHER_ONLY;
      if (mode.equals("full")) view = QuoteFavoritesBookBuilder.ViewMode.FULL;

      // ✅ count は “お気に入り総数”
      int count = (rows == null) ? 0 : rows.size();

      ItemStack book = bookBuilder.buildFavoritesBook(lang, uuid, count, rows, view);
      if (book == null) {
        player.sendMessage(ChatColor.RED + "Failed to open favorites book.");
        return true;
      }

      try {
        player.openBook(book);
      } catch (Throwable t) {
        player.sendMessage(ChatColor.RED + "openBook() failed on this server.");
      }
      return true;
    }

    player.sendMessage(ChatColor.RED + "Unknown subcommand. Use: /quoteFavorite help");
    return true;
  }

  private void showHelp(Player player) {
    player.sendMessage(ChatColor.AQUA + "[TreasureRun] Quote Favorite Commands:");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite latest          - save latest quote as favorite");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite list            - show favorites");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite remove <id>     - remove favorite");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite reread          - random reread (chat)");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite reread title    - random reread (title)");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite reread book     - random reread (book)");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite book            - open catalog (full)");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite book toc        - open catalog (TOC only)");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite book success    - open SUCCESS chapter");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite book timeup     - open TIME_UP chapter");
    player.sendMessage(ChatColor.GRAY + "/quoteFavorite book other      - open OTHER chapter");
  }

  // =======================================================
  // ✅ pluginが持つ I18n を “同じ参照” で拾う（/treasureReload で即反映）
  // - 取れなければ new I18n(plugin) で最低限動く
  // =======================================================
  private static I18n resolveI18n(TreasureRunMultiChestPlugin plugin) {
    if (plugin == null) return null;

    // 1) getI18n() がある場合
    try {
      java.lang.reflect.Method m = plugin.getClass().getMethod("getI18n");
      Object v = m.invoke(plugin);
      if (v instanceof I18n i) return i;
    } catch (Throwable ignored) {}

    // 2) private field "i18n" を反射で拾う
    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("i18n");
      f.setAccessible(true);
      Object v = f.get(plugin);
      if (v instanceof I18n i) return i;
    } catch (Throwable ignored) {}

    // 3) fallback
    try {
      I18n i = new I18n(plugin);
      i.loadOrCreate();
      return i;
    } catch (Throwable ignored) {}

    return null;
  }

  // =======================================================
  // ✅ PlayerLanguageStore があればそれを優先して言語取得
  // - 見つからなければ config default
  // =======================================================
  private String resolvePlayerLang(Player player) {
    if (player == null) return plugin.getConfig().getString("language.default", "ja");

    UUID uuid = player.getUniqueId();

    // playerLanguageStore.get(uuid) を反射で拾う（クラス構造が変わっても壊れない）
    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("playerLanguageStore");
      f.setAccessible(true);
      Object store = f.get(plugin);

      if (store != null) {
        java.lang.reflect.Method m = store.getClass().getMethod("get", UUID.class);
        Object ret = m.invoke(store, uuid);
        if (ret instanceof String s && !s.isBlank()) {
          return s;
        }
      }
    } catch (Throwable ignored) {}

    return plugin.getConfig().getString("language.default", "ja");
  }

  // =======================================================
  // ✅ repository の String行を “kind/text” に変換してBuilderに渡す
  // - 例: "【SUCCESS / Normal / en】\nThe obstacle is the way."
  // =======================================================
  private static Object toRowObject(String raw) {
    if (raw == null) raw = "";

    String kind = "OTHER";
    String text = raw;

    // kind 推定（簡易）
    String upper = raw.toUpperCase(Locale.ROOT);
    if (upper.contains("SUCCESS")) kind = "SUCCESS";
    if (upper.contains("TIME_UP") || upper.contains("TIME UP") || upper.contains("TIMEUP")) kind = "TIME_UP";

    // text は括弧ラベル以降の本文だけにしたい場合は 1行目を落とす
    int idx = raw.indexOf("\n");
    if (idx >= 0 && idx + 1 < raw.length()) {
      text = raw.substring(idx + 1).trim();
    } else {
      text = raw.trim();
    }

    return new SimpleRow(kind, text);
  }

  // ✅ Builder 側が reflection で kind/text を読めるようにする最小Row
  public static class SimpleRow {
    private final String kind;
    private final String text;

    public SimpleRow(String kind, String text) {
      this.kind = (kind == null) ? "OTHER" : kind;
      this.text = (text == null) ? "" : text;
    }

    public String getKind() { return kind; }
    public String getText() { return text; }
  }
}