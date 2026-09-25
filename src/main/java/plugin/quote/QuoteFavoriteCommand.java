package plugin.quote;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import plugin.I18n;
import plugin.TreasureRunMultiChestPlugin;

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
      sender.sendMessage(trRaw("command.quoteFavorite.playersOnly"));
      return true;
    }

    if (args.length == 0) {
      showHelp(player);
      return true;
    }

    String sub = args[0].toLowerCase(Locale.ROOT);
    if (sub.equals("help")) {
      showHelp(player);
      return true;
    }

    UUID playerId = player.getUniqueId();
    InteractiveProverbService service = plugin.getInteractiveProverbService();

    if (sub.equals("latest")) {
      if (service == null) {
        player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.repositoryNotReady"));
        return true;
      }

      service.favoriteLatest(playerId).whenComplete((result, failure) ->
          deliver(playerId, current -> {
            if (failure != null || result == null || !result.successful()) {
              current.sendMessage(ChatColor.RED + tr(current, "command.quoteFavorite.repositoryNotReady"));
            } else if (Boolean.TRUE.equals(result.value())) {
              current.sendMessage(ChatColor.GREEN + tr(current, "command.quoteFavorite.latestSaved"));
            } else {
              current.sendMessage(ChatColor.RED + tr(current, "command.quoteFavorite.latestNotSaved"));
            }
          })
      );
      return true;
    }

    if (sub.equals("list")) {
      if (service == null) {
        player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.repositoryNotReady"));
        return true;
      }

      service.loadFavorites(playerId, 20).whenComplete((result, failure) ->
          deliver(playerId, current -> {
            if (failure != null || result == null || !result.successful()) {
              current.sendMessage(ChatColor.RED + tr(current, "command.quoteFavorite.repositoryNotReady"));
              return;
            }

            List<String> favs = result.value() == null ? List.of() : result.value();
            current.sendMessage(ChatColor.AQUA + trp(
                current, "command.quoteFavorite.listHeader", "count", String.valueOf(favs.size())));
            for (String row : favs) {
              String safeRow = sanitizeFavoriteRow(row);
              if (safeRow.isBlank()) continue;
              current.sendMessage(ChatColor.GRAY + tr(current, "command.quoteFavorite.listSeparator"));
              for (String line : safeRow.split("\\n")) {
                if (line == null || line.isBlank()) continue;
                current.sendMessage(ChatColor.WHITE + line);
              }
            }
          })
      );
      return true;
    }

    if (sub.equals("remove")) {
      if (args.length < 2) {
        player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.removeUsage"));
        return true;
      }

      int id;
      try {
        id = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.removeIdNumber"));
        return true;
      }

      if (service == null) {
        player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.repositoryNotReady"));
        return true;
      }

      service.deleteFavorite(playerId, id).whenComplete((result, failure) ->
          deliver(playerId, current -> {
            if (failure != null || result == null || !result.successful()) {
              current.sendMessage(ChatColor.RED + tr(current, "command.quoteFavorite.repositoryNotReady"));
            } else if (Boolean.TRUE.equals(result.value())) {
              current.sendMessage(ChatColor.GREEN + trp(
                  current, "command.quoteFavorite.removeSuccess", "id", String.valueOf(id)));
            } else {
              current.sendMessage(ChatColor.RED + tr(current, "command.quoteFavorite.removeNotFound"));
            }
          })
      );
      return true;
    }

    if (sub.equals("reread")) {
      String mode = (args.length >= 2) ? args[1].toLowerCase(Locale.ROOT) : "chat";
      QuoteRereadService.OutputMode outMode = QuoteRereadService.OutputMode.CHAT;
      if (mode.equals("title")) outMode = QuoteRereadService.OutputMode.TITLE;
      if (mode.equals("book")) outMode = QuoteRereadService.OutputMode.BOOK;
      rereadService.rereadRandom(player, outMode);
      return true;
    }

    if (sub.equals("book")) {
      if (service == null) {
        player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.repositoryNotReady"));
        return true;
      }

      String mode = (args.length >= 2) ? args[1].toLowerCase(Locale.ROOT) : "full";
      String lang = resolvePlayerLang(player);
      QuoteFavoritesBookBuilder.ViewMode view = QuoteFavoritesBookBuilder.ViewMode.FULL;
      if (mode.equals("toc")) view = QuoteFavoritesBookBuilder.ViewMode.TOC_ONLY;
      if (mode.equals("success")) view = QuoteFavoritesBookBuilder.ViewMode.SUCCESS_ONLY;
      if (mode.equals("timeup")) view = QuoteFavoritesBookBuilder.ViewMode.TIME_UP_ONLY;
      if (mode.equals("other")) view = QuoteFavoritesBookBuilder.ViewMode.OTHER_ONLY;
      QuoteFavoritesBookBuilder.ViewMode requestedView = view;

      service.loadFavorites(playerId, 200).whenComplete((result, failure) ->
          deliver(playerId, current -> {
            if (failure != null || result == null || !result.successful()) {
              current.sendMessage(ChatColor.RED + tr(current, "command.quoteFavorite.repositoryNotReady"));
              return;
            }

            List<String> rawRows = result.value() == null ? List.of() : result.value();
            List<Object> rows = rawRows.stream()
                .map(QuoteFavoriteCommand::toRowObject)
                .collect(Collectors.toList());

            ItemStack book = bookBuilder.buildFavoritesBook(
                lang, playerId, rows.size(), rows, requestedView);
            if (book == null) {
              current.sendMessage(ChatColor.RED + tr(current, "command.quoteFavorite.bookOpenFailed"));
              return;
            }

            try {
              current.openBook(book);
            } catch (Throwable t) {
              current.sendMessage(ChatColor.RED + tr(current, "command.quoteFavorite.openBookFailed"));
            }
          })
      );
      return true;
    }

    player.sendMessage(ChatColor.RED + tr(player, "command.quoteFavorite.unknownSubcommand"));
    return true;
  }

  private void deliver(UUID playerId, java.util.function.Consumer<Player> action) {
    try {
      plugin.getServer().getScheduler().runTask(plugin, () -> {
        if (!plugin.isEnabled()) return;
        Player current = plugin.getServer().getPlayer(playerId);
        if (current == null || !current.isOnline()) return;
        action.accept(current);
      });
    } catch (RuntimeException ignored) {
      // Plugin lifecycle is shutting down. Late DB results are intentionally dropped.
    }
  }

  private void showHelp(Player player) {
    player.sendMessage(ChatColor.AQUA + tr(player, "command.quoteFavorite.help.title"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.latest"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.list"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.remove"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.reread"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.rereadTitle"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.rereadBook"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.book"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.bookToc"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.bookSuccess"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.bookTimeup"));
    player.sendMessage(ChatColor.GRAY + tr(player, "command.quoteFavorite.help.bookOther"));
  }



  private String sanitizeFavoriteRow(String row) {
    if (row == null || row.isBlank()) return "";
    StringBuilder out = new StringBuilder();
    for (String line : row.split("\\n")) {
      if (line == null) continue;
      String t = line.trim();
      if (t.isEmpty()) continue;
      if (t.contains("Translation missing:")) continue;
      if (out.length() > 0) out.append("\n");
      out.append(line);
    }
    return out.toString().trim();
  }

  private String tr(Player player, String key) {
    String lang = resolvePlayerLang(player);
    try {
      if (i18n != null) {
        String value = i18n.tr(lang, key);
        if (value != null && !value.isBlank() && !value.equals(key)
            && !value.startsWith("Translation missing:")) {
          return value;
        }
      }
    } catch (Throwable ignored) { }
    return key;
  }

  private String trp(Player player, String key, String name, String value) {
    String lang = resolvePlayerLang(player);
    try {
      if (i18n != null) {
        String translated = i18n.tr(lang, key, java.util.Map.of(name, value));
        if (translated != null && !translated.isBlank() && !translated.equals(key)
            && !translated.startsWith("Translation missing:")) {
          return translated;
        }
      }
    } catch (Throwable ignored) { }
    return key.replace("{" + name + "}", value);
  }

  private String trRaw(String key) {
    try {
      String lang = plugin.getConfig().getString("language.default", "ja");
      if (i18n != null) {
        String s = i18n.tr(lang, key);
        if (s != null && !s.isBlank() && !s.equals(key) && !s.startsWith("Translation missing:")) return s;
      }
    } catch (Throwable ignored) {}
    return key;
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
    String def = plugin.getConfig().getString("language.default", "ja");
    if (player == null) return def;

    try {
      if (plugin.getPlayerLanguageStore() != null) {
        String saved = plugin.getPlayerLanguageStore().getLang(player.getUniqueId(), "");
        if (saved != null && !saved.isBlank()) return saved;
      }
    } catch (Throwable ignored) {}

    try {
      if (plugin.getLanguageStore() != null) {
        String mem = plugin.getLanguageStore().get(player.getUniqueId());
        if (mem != null && !mem.isBlank()) return mem;
      }
    } catch (Throwable ignored) {}

    return def;
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