package plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class LangCommand implements CommandExecutor, TabCompleter {

  private final TreasureRunMultiChestPlugin plugin;

  public LangCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    // Player限定（コンソール対応したい場合はここを拡張）
    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
      return true;
    }

    // allowedLanguages を config から取得
    List<String> allowed = getAllowedLanguagesFromConfig();
    String defaultLang = plugin.getConfig().getString("language.default", "ja");

    // 引数なし → GUIを開く（最も分かりやすい）
    if (args.length == 0) {
      openLanguageGuiOrFallbackMessage(player, allowed, defaultLang);
      return true;
    }

    String sub = args[0].trim();

    // ✅ reset：保存済み言語を削除して「未設定」に戻す
    if (sub.equalsIgnoreCase("reset")) {
      try {
        if (plugin.getPlayerLanguageStore() != null) {
          plugin.getPlayerLanguageStore().clear(player.getUniqueId());
        }
      } catch (Throwable ignored) {}

      // LanguageStore 側にも残っていれば消す（任意・安全に反射）
      try {
        LanguageStore ls = plugin.getLanguageStore();
        if (ls != null) {
          String[] candidates = {"clear", "remove", "unset", "delete", "clearPlayerLanguage", "removePlayerLanguage"};
          for (String mname : candidates) {
            try {
              Method m = ls.getClass().getMethod(mname, UUID.class);
              m.invoke(ls, player.getUniqueId());
              break;
            } catch (NoSuchMethodException ignore) {}
          }
        }
      } catch (Throwable ignored) {}

      player.sendMessage(ChatColor.GREEN + "✅ 保存済み言語をリセットしました（次回はGUIが出ます）");
      return true;
    }

    // list / current / gui
    if (sub.equalsIgnoreCase("list")) {
      sendLanguageList(player, allowed, defaultLang);
      return true;
    }

    // list / current / gui
    if (sub.equalsIgnoreCase("list")) {
      sendLanguageList(player, allowed, defaultLang);
      return true;
    }
    if (sub.equalsIgnoreCase("current")) {
      String current = resolveCurrentLang(player.getUniqueId(), defaultLang);
      player.sendMessage(ChatColor.AQUA + "現在の言語: " + ChatColor.YELLOW + current);
      return true;
    }
    if (sub.equalsIgnoreCase("gui")) {
      openLanguageGuiOrFallbackMessage(player, allowed, defaultLang);
      return true;
    }

    // /lang <code> で直接切替
    String lang = normalizeLangCode(sub);
    if (lang.isBlank()) {
      player.sendMessage(ChatColor.RED + "言語コードが空です。例: /lang ja");
      return true;
    }

    if (!allowed.contains(lang)) {
      player.sendMessage(ChatColor.RED + "その言語は許可されていません: " + lang);
      player.sendMessage(ChatColor.GRAY + "許可されている言語: " + ChatColor.YELLOW + String.join(", ", allowed));
      return true;
    }

    // ✅ ここが本体：言語を保存する（永続）
    boolean saved = setPlayerLang(player.getUniqueId(), lang);

    // 可能なら LanguageStore にも反映（存在/メソッド名が違っても落ちないよう反射）
    trySetLangToLanguageStore(player.getUniqueId(), lang);

    // 表示名（configの language.displayName から）
    String displayName = plugin.getConfig().getString("language.displayName." + lang, lang);

    if (saved) {
      player.sendMessage(ChatColor.GREEN + "✅ 言語を変更しました: " + ChatColor.AQUA + displayName + ChatColor.GRAY + " (" + lang + ")");
    } else {
      player.sendMessage(ChatColor.YELLOW + "⚠ 言語は変更しましたが、永続保存に失敗しました: " + lang);
    }

    // 任意：I18nがあるなら動作確認用に一言（キーはあなたのI18n設計次第なので安全に）
    // 例: command.lang.changed がある場合だけ表示
    trySendI18nTestMessage(player, lang);

    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!(sender instanceof Player)) return Collections.emptyList();

    List<String> allowed = getAllowedLanguagesFromConfig();

    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);

      List<String> base = new ArrayList<>();
      base.add("list");
      base.add("current");
      base.add("gui");
      base.addAll(allowed);

      return base.stream()
          .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
          .distinct()
          .sorted()
          .collect(Collectors.toList());
    }

    return Collections.emptyList();
  }

  // =======================================================
  // helpers
  // =======================================================

  private List<String> getAllowedLanguagesFromConfig() {
    List<String> list = plugin.getConfig().getStringList("language.allowedLanguages");
    if (list == null || list.isEmpty()) {
      // 何もなければ最低限
      return new ArrayList<>(Arrays.asList("ja", "en"));
    }
    // 正規化して重複排除
    LinkedHashSet<String> set = new LinkedHashSet<>();
    for (String s : list) {
      String n = normalizeLangCode(s);
      if (!n.isBlank()) set.add(n);
    }
    return new ArrayList<>(set);
  }

  private String normalizeLangCode(String raw) {
    if (raw == null) return "";
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private void sendLanguageList(Player player, List<String> allowed, String defaultLang) {
    player.sendMessage(ChatColor.AQUA + "=== Languages ===");
    player.sendMessage(ChatColor.GRAY + "default: " + ChatColor.YELLOW + defaultLang);
    player.sendMessage(ChatColor.GRAY + "allowed: " + ChatColor.YELLOW + String.join(", ", allowed));
    player.sendMessage(ChatColor.GRAY + "使い方: " + ChatColor.YELLOW + "/lang <code>" + ChatColor.GRAY + " 例: /lang ja");
    player.sendMessage(ChatColor.GRAY + "GUI: " + ChatColor.YELLOW + "/lang" + ChatColor.GRAY + " または /lang gui");
  }

  private void openLanguageGuiOrFallbackMessage(Player player, List<String> allowed, String defaultLang) {
    // GUIがあるならそれを開く
    try {
      LanguageSelectGui gui = plugin.getLanguageSelectGui();
      if (gui != null) {
        // 難易度が必要なので一旦 Normal を渡す（あなたの既存設計に合わせる）
        gui.open(player, "Normal");
        return;
      }
    } catch (Throwable ignored) {}

    // GUIが無い場合のフォールバック
    player.sendMessage(ChatColor.RED + "Language GUI が初期化されていません。");
    sendLanguageList(player, allowed, defaultLang);
  }

  /**
   * 現在言語の解決：
   * 1) PlayerLanguageStore（永続）
   * 2) LanguageStore（もし存在すれば）
   * 3) default
   */
  private String resolveCurrentLang(UUID uuid, String defaultLang) {
    String fromPLS = tryGetLangFromPlayerLanguageStore(uuid);
    if (fromPLS != null && !fromPLS.isBlank()) return fromPLS;

    String fromLS = tryGetLangFromLanguageStore(uuid);
    if (fromLS != null && !fromLS.isBlank()) return fromLS;

    return defaultLang;
  }

  /**
   * ✅ 言語を保存（永続）
   * PlayerLanguageStore が存在する前提で反射で set/get を試す
   */
  private boolean setPlayerLang(UUID uuid, String lang) {
    if (uuid == null) return false;

    // まず plugin 内フィールド playerLanguageStore を反射で取得して set を試す
    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("playerLanguageStore");
      f.setAccessible(true);
      Object pls = f.get(plugin);
      if (pls == null) return false;

      // set(UUID, String) を試す
      try {
        Method m = pls.getClass().getMethod("set", UUID.class, String.class);
        m.invoke(pls, uuid, lang);
        return true;
      } catch (NoSuchMethodException ignore) {
        // setLanguage(UUID, String) など別名も試す
        String[] candidates = {"setLanguage", "setLang", "put", "save"};
        for (String name : candidates) {
          try {
            Method m2 = pls.getClass().getMethod(name, UUID.class, String.class);
            m2.invoke(pls, uuid, lang);
            return true;
          } catch (NoSuchMethodException ignored2) {
          }
        }
      }

    } catch (Throwable t) {
      plugin.getLogger().warning("[Lang] Failed to save language: " + t.getMessage());
    }
    return false;
  }

  private String tryGetLangFromPlayerLanguageStore(UUID uuid) {
    if (uuid == null) return null;
    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("playerLanguageStore");
      f.setAccessible(true);
      Object pls = f.get(plugin);
      if (pls == null) return null;

      // get(UUID) / getLanguage(UUID) / getLang(UUID) あたりを試す
      String[] candidates = {"get", "getLanguage", "getLang", "load"};
      for (String name : candidates) {
        try {
          Method m = pls.getClass().getMethod(name, UUID.class);
          Object ret = m.invoke(pls, uuid);
          if (ret instanceof String s && !s.isBlank()) return s;
        } catch (NoSuchMethodException ignored) {
        }
      }
    } catch (Throwable ignored) {}
    return null;
  }

  /**
   * LanguageStore にも反映（存在していれば）
   * メソッド名が違っても落ちないように候補を試す
   */
  private void trySetLangToLanguageStore(UUID uuid, String lang) {
    if (uuid == null) return;
    try {
      LanguageStore store = plugin.getLanguageStore();
      if (store == null) return;

      String[] candidates = {
          "setPlayerLanguage",
          "setSelectedLanguage",
          "setLang",
          "setLanguage",
          "setPlayerLang"
      };

      for (String methodName : candidates) {
        try {
          Method m = store.getClass().getMethod(methodName, UUID.class, String.class);
          m.invoke(store, uuid, lang);
          return;
        } catch (NoSuchMethodException ignore) {
        }
      }
    } catch (Throwable ignored) {}
  }

  private String tryGetLangFromLanguageStore(UUID uuid) {
    if (uuid == null) return null;
    try {
      LanguageStore store = plugin.getLanguageStore();
      if (store == null) return null;

      String[] candidates = {
          "getPlayerLanguage",
          "getSelectedLanguage",
          "getLang",
          "getLanguage",
          "getPlayerLang"
      };

      for (String methodName : candidates) {
        try {
          Method m = store.getClass().getMethod(methodName, UUID.class);
          Object ret = m.invoke(store, uuid);
          if (ret instanceof String s && !s.isBlank()) return s;
        } catch (NoSuchMethodException ignore) {
        }
      }
    } catch (Throwable ignored) {}
    return null;
  }

  /**
   * I18n がある場合だけ、キーが存在しそうならテスト表示。
   * ※あなたの I18n 設計（languages/*.yml のキー）に合わせて後で調整してOK。
   */
  private void trySendI18nTestMessage(Player player, String lang) {
    // ここでは「I18nクラスが存在しても、pluginフィールド名やメソッド名が違う」可能性があるので反射で安全に。
    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("i18n");
      f.setAccessible(true);
      Object i18n = f.get(plugin);
      if (i18n == null) return;

      // I18n.tr(Player, key) / I18n.tr(UUID,key) / I18n.tr(null,key) 等、実装差があるので候補を試す
      String key = "command.lang.changed"; // ←languages/*.yml 側でこのキーを作ると便利
      String msg = tryI18nTr(i18n, player, key);

      if (msg != null && !msg.isBlank() && !msg.equals(key)) {
        player.sendMessage(ChatColor.GRAY + msg + ChatColor.DARK_GRAY + " (" + lang + ")");
      }
    } catch (Throwable ignored) {}
  }

  private String tryI18nTr(Object i18n, Player player, String key) {
    if (i18n == null) return null;

    // 候補1: tr(Player, String)
    try {
      Method m = i18n.getClass().getMethod("tr", Player.class, String.class);
      Object ret = m.invoke(null, player, key); // static想定
      if (ret instanceof String s) return s;
    } catch (Throwable ignored) {}

    // 候補2: tr(UUID, String)
    try {
      Method m = i18n.getClass().getMethod("tr", UUID.class, String.class);
      Object ret = m.invoke(null, player.getUniqueId(), key);
      if (ret instanceof String s) return s;
    } catch (Throwable ignored) {}

    // 候補3: tr(Object, String)
    try {
      Method m = i18n.getClass().getMethod("tr", Object.class, String.class);
      Object ret = m.invoke(null, player, key);
      if (ret instanceof String s) return s;
    } catch (Throwable ignored) {}

    return null;
  }
}