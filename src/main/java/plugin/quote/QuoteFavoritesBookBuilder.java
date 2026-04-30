package plugin.quote;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import plugin.I18n;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static plugin.quote.QuoteFavoriteKeys.*;

/**
 * ✅ Favorites 図鑑 Builder（19言語対応）
 *
 * - すべて I18n.tr() 経由で messages.yml を読む
 * - lang -> en -> ja -> default fallback は I18n 側で保証
 * - QuoteFavoriteCommand / QuoteRereadService から呼べる互換構成
 */
public class QuoteFavoritesBookBuilder {

  public enum ViewMode {
    FULL,
    TOC_ONLY,
    SUCCESS_ONLY,
    TIME_UP_ONLY,
    OTHER_ONLY
  }

  private final I18n i18n;

  /** ✅ 推奨：外から I18n を渡す（/treasureReload でも統一できる） */
  public QuoteFavoritesBookBuilder(I18n i18n) {
    this.i18n = i18n;
  }

  /** ✅ 互換：既存コードが plugin を渡してくる場合でも動くようにする */
  public QuoteFavoritesBookBuilder(JavaPlugin plugin) {
    I18n loaded = new I18n(plugin);
    loaded.loadOrCreate();
    this.i18n = loaded;
  }

  /** ✅ 互換：引数なしで new してる場所が残っていてもビルドを落とさない（最後の保険） */
  public QuoteFavoritesBookBuilder() {
    this.i18n = null; // ※ i18n無しの利用は想定しない（ビルド用保険）
  }

  // =======================================================
  // ✅ 公開API
  // =======================================================

  /**
   * ✅ Favorites Book（標準 FULL）
   */
  public ItemStack buildFavoritesBook(String lang, UUID playerUuid, int count, List<?> rows) {
    return buildFavoritesBook(lang, playerUuid, count, rows, ViewMode.FULL);
  }

  /**
   * ✅ Favorites Book（表示モード切替）
   */
  public ItemStack buildFavoritesBook(String lang, UUID playerUuid, int count, List<?> rows, ViewMode mode) {
    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) book.getItemMeta();

    // i18n が null なら最低限の本だけ返す（ビルド用保険）
    if (i18n == null || meta == null) {
      if (meta != null) {
        meta.setTitle("TreasureRun");
        meta.setAuthor("TreasureRun");
        meta.setPages(List.of("TreasureRun"));
        book.setItemMeta(meta);
      }
      return book;
    }

    meta.setTitle(tr(lang, TITLE, "Favorites"));
    meta.setAuthor("TreasureRun");

    List<String> pages = new ArrayList<>();

    // --- Cover
    pages.add(
        tr(lang, COVER_HEAD, "Favorites Archive") + "\n\n"
            + tr(lang, COVER_SUB, "Saved lines from your runs") + "\n\n"
            + tr(lang, COVER_BADGE, "TreasureRun Favorites") + "\n"
            + trp(lang, COVER_COUNT_LABEL, Map.of("count", String.valueOf(count)), "Saved: {count}") + "\n\n"
            + tr(lang, COVER_HINT, "Use /quoteFavorite latest or /quoteFavorite list")
    );

    // --- TOC
    pages.add(
        tr(lang, TOC_HEAD, "Contents") + "\n"
            + tr(lang, TOC_SUB, "Browse by chapter") + "\n\n"
            + "1. " + tr(lang, CHAPTER_SUCCESS_TITLE, "Success") + "\n"
            + "2. " + tr(lang, CHAPTER_TIMEUP_TITLE, "時尽きぬ") + "\n"
            + "3. " + tr(lang, CHAPTER_OTHER_SUB, "Other") + "\n\n"
            + tr(lang, TOC_HOWTO, "Open a chapter to reread saved lines") + "\n"
            + tr(lang, TOC_HOWTO_SHIFT, "Shift+RightClick saves the latest line")
    );

    // TOC_ONLY ならここで終了
    if (mode == ViewMode.TOC_ONLY) {
      meta.setPages(pages);
      book.setItemMeta(meta);
      return book;
    }

    // rows が空なら “空の説明ページ”
    if (rows == null || rows.isEmpty()) {
      pages.add(
          tr(lang, EMPTY_HEAD, "No favorites yet") + "\n\n"
              + tr(lang, EMPTY_SUB, "Your archive is still empty") + "\n\n"
              + tr(lang, EMPTY_NO_FAV, "No favorites saved yet") + "\n"
              + tr(lang, EMPTY_NO_FAV_SUB, "Save a line first to begin your archive") + "\n\n"
              + tr(lang, EMPTY_SAVE_HOW, "How to save favorites") + "\n"
              + "• " + tr(lang, EMPTY_SAVE_HOW_1, "Use /quoteFavorite latest") + "\n"
              + "• " + tr(lang, EMPTY_SAVE_HOW_2, "Or Shift+RightClick the rule book") + "\n\n"
              + tr(lang, EMPTY_TRY_NOW, "Then open this book again")
      );

      meta.setPages(pages);
      book.setItemMeta(meta);
      return book;
    }

    // ✅ outcome(kind) で分類（reflectionで壊れない）
    // ✅ 修正版：List<?> の capture を消す（互換性のない型エラー対策）
    Map<String, List<Object>> byKind = rows.stream()
        .map(o -> (Object) o) // ✅ capture消し
        .collect(Collectors.groupingBy(this::extractKindSafe));

    if (mode == ViewMode.FULL || mode == ViewMode.SUCCESS_ONLY) {
      pages.add(makeChapter(
          lang,
          tr(lang, CHAPTER_SUCCESS_TITLE, "Success"),
          byKind.getOrDefault("SUCCESS", List.of()),
          tr(lang, SUCCESS_TEMPLATE_1, "Saved success lines"),
          tr(lang, SUCCESS_TEMPLATE_2, "Moments you cleared the run")
      ));
    }

    if (mode == ViewMode.FULL || mode == ViewMode.TIME_UP_ONLY) {
      pages.add(makeChapter(
          lang,
          tr(lang, CHAPTER_TIMEUP_TITLE, "時尽きぬ"),
          byKind.getOrDefault("TIME_UP", List.of()),
          tr(lang, TIMEUP_TEMPLATE_1, "Saved time-up lines"),
          tr(lang, TIMEUP_TEMPLATE_2, "Moments the timer ran out")
      ));
    }

    if (mode == ViewMode.FULL || mode == ViewMode.OTHER_ONLY) {
      pages.add(makeChapter(
          lang,
          tr(lang, CHAPTER_OTHER_SUB, "Other"),
          byKind.getOrDefault("OTHER", List.of()),
          tr(lang, SUCCESS_TEMPLATE_1, "Saved success lines"),
          tr(lang, SUCCESS_TEMPLATE_2, "Moments you cleared the run")
      ));
    }

    meta.setPages(pages);
    book.setItemMeta(meta);
    return book;
  }

  /**
   * ✅ 1つだけ読み返す「OneShot Book」（QuoteRereadService 用）
   */
  public ItemStack buildRereadOneShotBook(org.bukkit.entity.Player player, Object row) {
    String lang = inferLangFromPlayer(player);

    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) book.getItemMeta();
    if (meta == null) return book;

    String quoteText = extractTextSafe(row);
    if (quoteText == null || quoteText.isBlank()) {
      quoteText = tr(lang, "favorites.reread.noQuotes", "No saved lines yet.");
    }

    String title = tr(lang, COVER_REREAD, tr(lang, TITLE, "Favorites"));
    String head = tr(lang, "favorites.reread.head", "Saved lines reread");

    meta.setTitle(title);
    meta.setAuthor("TreasureRun");
    meta.setPages(List.of(head + "\n\n" + quoteText));

    book.setItemMeta(meta);
    return book;
  }
  // =======================================================
  // ✅ 内部：Chapter生成
  // =======================================================


  private String inferLangFromPlayer(org.bukkit.entity.Player player) {
    if (player == null) return "ja";

    try {
      String locale = player.getLocale();
      if (locale == null || locale.isBlank()) return "ja";

      String l = locale.toLowerCase(Locale.ROOT).replace('-', '_');

      if (l.startsWith("ja")) return "ja";
      if (l.startsWith("en")) return "en";
      if (l.startsWith("de")) return "de";
      if (l.startsWith("sv")) return "sv";
      if (l.startsWith("zh_tw") || l.startsWith("zh_hant")) return "zh_tw";
      if (l.startsWith("fr")) return "fr";
      if (l.startsWith("es")) return "es";
      if (l.startsWith("it")) return "it";
      if (l.startsWith("pt")) return "pt";
      if (l.startsWith("fi")) return "fi";
      if (l.startsWith("hi")) return "hi";
      if (l.startsWith("is")) return "is";
      if (l.startsWith("ko")) return "ko";
      if (l.startsWith("la")) return "la";
      if (l.startsWith("lzh")) return "lzh";
      if (l.startsWith("nl")) return "nl";
      if (l.startsWith("ru")) return "ru";
      if (l.startsWith("sa")) return "sa";
      if (l.startsWith("asl")) return "asl_gloss";
    } catch (Throwable ignored) {}

    return "ja";
  }

  private String makeChapter(String lang, String title, List<?> rows, String tpl1, String tpl2) {
    StringBuilder sb = new StringBuilder();
    sb.append(title).append("\n\n");

    if (rows == null || rows.isEmpty()) {
      sb.append(tr(lang, CHAPTER_EMPTY, "No saved lines yet")).append("\n");
      return sb.toString();
    }

    // 章テンプレ
    sb.append(tpl1).append("\n");
    sb.append(tpl2).append("\n\n");

    int idx = 1;
    for (Object r : rows) {
      String text = extractTextSafe(r);
      if (text.isBlank()) continue;

      sb.append(idx).append(". ").append(text).append("\n\n");
      idx++;
      if (idx > 10) break; // 1章最大10件（見やすさ優先）
    }

    sb.append("\n").append(trp(lang, CHAPTER_COUNT, Map.of("count", String.valueOf(rows.size())), "{count} entries"));
    return sb.toString();
  }

  // =======================================================
  // ✅ ここが “壊れない” の肝：kind/text 抽出（reflection）
  // =======================================================

  private String extractKindSafe(Object row) {
    String kind = readStringFieldOrGetter(row, "kind");
    if (kind.isBlank()) kind = readStringFieldOrGetter(row, "outcome");
    if (kind.isBlank()) kind = readStringFieldOrGetter(row, "result");
    if (kind.isBlank()) kind = "OTHER";

    // normalize
    kind = kind.trim().toUpperCase(Locale.ROOT);
    if (kind.contains("TIME")) return "TIME_UP";
    if (kind.contains("SUCCESS")) return "SUCCESS";
    if (kind.contains("FAIL")) return "OTHER";
    if (kind.contains("OTHER")) return "OTHER";
    return kind;
  }

  private String extractTextSafe(Object row) {
    String text = readStringFieldOrGetter(row, "text");
    if (text.isBlank()) text = readStringFieldOrGetter(row, "quote");
    if (text.isBlank()) text = readStringFieldOrGetter(row, "message");
    if (text.isBlank()) text = readStringFieldOrGetter(row, "value");
    if (text == null) return "";
    text = text.trim();
    if (text.contains("Translation missing:")) return "";
    return text;
  }

  private String readStringFieldOrGetter(Object obj, String name) {
    if (obj == null || name == null || name.isBlank()) return "";

    // getter: getXxx()
    try {
      String m = "get" + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
      Method method = obj.getClass().getMethod(m);
      Object v = method.invoke(obj);
      if (v instanceof String) return (String) v;
      if (v != null) return String.valueOf(v);
    } catch (Exception ignored) {
    }

    // field: xxx
    try {
      Field f = obj.getClass().getDeclaredField(name);
      f.setAccessible(true);
      Object v = f.get(obj);
      if (v instanceof String) return (String) v;
      if (v != null) return String.valueOf(v);
    } catch (Exception ignored) {
    }

    return "";
  }

  // =======================================================
  // ✅ compatibility: called by QuoteFavoriteBookClickListener
  // =======================================================

  public org.bukkit.inventory.ItemStack buildEmptyFavoritesBook(org.bukkit.entity.Player player) {
    return buildFavoritesBook(player, java.util.Collections.emptyList());
  }

  public org.bukkit.inventory.ItemStack buildFavoritesBook(org.bukkit.entity.Player player, java.util.List<String> rows) {
    if (player == null) {
      return buildFavoritesBook("en", new java.util.UUID(0L, 0L), 0, rows);
    }

    String lang = inferLangFromPlayer(player);
    // 可能なら PlayerLanguageStore から拾う（フィールドが無くても落ちない）
    try {
      java.lang.reflect.Field f = this.getClass().getDeclaredField("playerLanguageStore");
      f.setAccessible(true);
      Object pls = f.get(this);
      if (pls != null) {
        java.lang.reflect.Method m = pls.getClass().getMethod("getPlayerLang", java.util.UUID.class);
        Object r = m.invoke(pls, player.getUniqueId());
        if (r != null) lang = String.valueOf(r);
      }
    } catch (Throwable ignored) {}

    int count = (rows == null ? 0 : rows.size());
    return buildFavoritesBook(lang, player.getUniqueId(), count, rows);
  }



  private String tr(String lang, String key, String fallback) {
    try {
      if (i18n == null) return fallback;
      String s = i18n.tr(lang, key);
      if (s == null || s.isBlank() || s.equals(key) || s.startsWith("Translation missing:")) return fallback;
      return s;
    } catch (Throwable ignored) {
      return fallback;
    }
  }

  private String trp(String lang, String key, Map<String, String> vars, String fallback) {
    try {
      if (i18n == null) return fallback;
      String s = i18n.tr(lang, key, vars);
      if (s == null || s.isBlank() || s.equals(key) || s.startsWith("Translation missing:")) s = fallback;
      if (vars != null) {
        for (Map.Entry<String, String> e : vars.entrySet()) {
          s = s.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
      }
      return s;
    } catch (Throwable ignored) {
      String s = fallback;
      if (vars != null) {
        for (Map.Entry<String, String> e : vars.entrySet()) {
          s = s.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
      }
      return s;
    }
  }

}
