package plugin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

public class QuoteFavoritesBookBuilder {

  // ✅ 文字数制限は環境で揺れるので余裕を持つ
  private static final int PAGE_CHAR_LIMIT = 230;

  // ✅ Book表示モード（目次/章単体）
  public enum ViewMode {
    FULL,        // 目次 + SUCCESS + TIME_UP (+OTHER)
    SUCCESS_ONLY,
    TIME_UP_ONLY,
    TOC_ONLY,
    OTHER_ONLY   // ✅ 追加：OTHER章だけ表示
  }

  // =======================================================
  // ✅ Favoritesがある時：図鑑本（クリック目次付き）
  // =======================================================
  public ItemStack buildFavoritesBook(Player player, List<String> rows) {
    return buildFavoritesBook(player, rows, ViewMode.FULL);
  }

  public ItemStack buildFavoritesBook(Player player, List<String> rows, ViewMode mode) {
    if (player == null) return null;

    try {
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta) book.getItemMeta();
      if (meta == null) return null;

      meta.setTitle("TreasureRun Favorites");
      meta.setAuthor(player.getName());
      meta.setDisplayName(org.bukkit.ChatColor.AQUA + "TreasureRun Favorites");

      if (rows == null || rows.isEmpty()) {
        // ✅ 0件なら「No favorites yet」本を返す
        return buildEmptyFavoritesBook(player);
      }

      // ✅ 章分け
      List<String> success = new ArrayList<>();
      List<String> timeUp = new ArrayList<>();
      List<String> other = new ArrayList<>();

      for (String r : rows) {
        if (r == null) continue;
        String raw = r.trim();
        if (raw.isEmpty()) continue;

        String upper = org.bukkit.ChatColor.stripColor(raw).toUpperCase();
        if (upper.contains("SUCCESS")) {
          success.add(raw);
        } else if (upper.contains("TIME_UP") || upper.contains("TIME UP")) {
          timeUp.add(raw);
        } else {
          other.add(raw);
        }
      }

      // ✅ ページを BaseComponent（クリック可能）で作る
      List<BaseComponent[]> pages = new ArrayList<>();

      // ✅ TOC（目次）
      if (mode == ViewMode.FULL || mode == ViewMode.TOC_ONLY) {
        pages.add(buildTocPage(success.size(), timeUp.size(), other.size()));
      }

      // ✅ SUCCESS章
      if (mode == ViewMode.FULL || mode == ViewMode.SUCCESS_ONLY) {
        if (!success.isEmpty()) {
          pages.addAll(buildChapterPages("SUCCESS", ChatColor.GREEN, success));
        } else if (mode == ViewMode.SUCCESS_ONLY) {
          pages.add(buildEmptyChapter("SUCCESS"));
        }
      }

      // ✅ TIME_UP章
      if (mode == ViewMode.FULL || mode == ViewMode.TIME_UP_ONLY) {
        if (!timeUp.isEmpty()) {
          pages.addAll(buildChapterPages("TIME_UP", ChatColor.GOLD, timeUp));
        } else if (mode == ViewMode.TIME_UP_ONLY) {
          pages.add(buildEmptyChapter("TIME_UP"));
        }
      }

      // ✅ OTHER章（FULLの時のみ）
      if (mode == ViewMode.FULL && !other.isEmpty()) {
        pages.addAll(buildChapterPages("OTHER", ChatColor.AQUA, other));
      }

      // ✅ ✅ ✅ 追加：OTHER_ONLYモード
      if (mode == ViewMode.OTHER_ONLY) {
        if (!other.isEmpty()) {
          pages.addAll(buildChapterPages("OTHER", ChatColor.AQUA, other));
        } else {
          pages.add(buildEmptyChapter("OTHER"));
        }
      }

      if (pages.isEmpty()) {
        // 念のため
        return buildEmptyFavoritesBook(player);
      }

      // ✅ SpigotのBookMetaは componentページを入れられる
      meta.spigot().setPages(pages);
      book.setItemMeta(meta);
      return book;

    } catch (Throwable t) {
      return null;
    }
  }

  // =======================================================
  // ✅ Favorites 0件なら「No favorites yet」を本で表示
  // =======================================================
  public ItemStack buildEmptyFavoritesBook(Player player) {
    if (player == null) return null;

    try {
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta) book.getItemMeta();
      if (meta == null) return null;

      meta.setTitle("TreasureRun Favorites");
      meta.setAuthor(player.getName());
      meta.setDisplayName(org.bukkit.ChatColor.AQUA + "TreasureRun Favorites");

      List<BaseComponent[]> pages = new ArrayList<>();
      pages.add(buildNoFavoritesPage());

      meta.spigot().setPages(pages);
      book.setItemMeta(meta);
      return book;

    } catch (Throwable t) {
      return null;
    }
  }

  // =======================================================
  // ✅ 目次ページ（クリック目次 + 再読ボタン）
  // =======================================================
  private BaseComponent[] buildTocPage(int successCount, int timeUpCount, int otherCount) {
    List<BaseComponent> out = new ArrayList<>();

    out.add(line("TreasureRun Archive", ChatColor.DARK_AQUA, true));
    out.add(line("Favorites Catalog", ChatColor.GRAY, false));
    out.add(new TextComponent("\n"));

    out.add(line("操作:", ChatColor.WHITE, true));
    out.add(line("Shift + 右クリックで開く", ChatColor.GRAY, false));
    out.add(new TextComponent("\n"));

    out.add(line("目次 (クリック可)", ChatColor.AQUA, true));
    out.add(button("▶ SUCCESS  (" + successCount + ")", "/quoteFavorite book success", ChatColor.GREEN));
    out.add(button("▶ TIME_UP  (" + timeUpCount + ")", "/quoteFavorite book timeup", ChatColor.GOLD));

    if (otherCount > 0) {
      // ✅ 修正：OTHERは toc ではなく other に飛ばす
      out.add(button("▶ OTHER  (" + otherCount + ")", "/quoteFavorite book other", ChatColor.AQUA));
    }

    out.add(new TextComponent("\n"));
    out.add(line("Re-read Mode", ChatColor.AQUA, true));
    out.add(button("▶ REREAD RANDOM (BOOK)", "/quoteFavorite reread book", ChatColor.LIGHT_PURPLE));

    out.add(new TextComponent("\n"));
    out.add(line("管理:", ChatColor.WHITE, true));
    out.add(line("/quoteFavorite list", ChatColor.GRAY, false));
    out.add(line("/quoteFavorite remove <id>", ChatColor.GRAY, false));

    return out.toArray(new BaseComponent[0]);
  }

  // =======================================================
  // ✅ Favorites 0件ページ（本で表示）
  // =======================================================
  private BaseComponent[] buildNoFavoritesPage() {
    List<BaseComponent> out = new ArrayList<>();

    out.add(line("TreasureRun Archive", ChatColor.DARK_AQUA, true));
    out.add(line("Favorites Catalog", ChatColor.GRAY, false));
    out.add(new TextComponent("\n"));

    out.add(line("No favorites yet.", ChatColor.YELLOW, true));
    out.add(line("まだお気に入りがありません。", ChatColor.GRAY, false));
    out.add(new TextComponent("\n"));

    out.add(line("保存方法:", ChatColor.WHITE, true));
    out.add(line("ルールブックを手に持って", ChatColor.GRAY, false));
    out.add(line("右クリック → 最新格言を保存", ChatColor.GRAY, false));
    out.add(new TextComponent("\n"));

    out.add(line("すぐ試す:", ChatColor.WHITE, true));
    out.add(button("▶ SAVE LATEST", "/quoteFavorite latest", ChatColor.GREEN));

    out.add(new TextComponent("\n"));
    out.add(line("再読:", ChatColor.WHITE, true));
    out.add(button("▶ REREAD RANDOM (BOOK)", "/quoteFavorite reread book", ChatColor.LIGHT_PURPLE));

    return out.toArray(new BaseComponent[0]);
  }

  // =======================================================
  // ✅ 空の章ページ
  // =======================================================
  private BaseComponent[] buildEmptyChapter(String title) {
    List<BaseComponent> out = new ArrayList<>();
    out.add(line("◆ " + title + " ◆", ChatColor.AQUA, true));
    out.add(new TextComponent("\n"));
    out.add(line("この章はまだ空です。", ChatColor.GRAY, false));
    out.add(new TextComponent("\n"));
    out.add(button("▶ Back to TOC", "/quoteFavorite book toc", ChatColor.AQUA));
    return out.toArray(new BaseComponent[0]);
  }

  // =======================================================
  // ✅ 章ページ（見出し強化 + 再読ボタン + ページ分割）
  // =======================================================
  private List<BaseComponent[]> buildChapterPages(String title, ChatColor color, List<String> items) {
    List<BaseComponent[]> pages = new ArrayList<>();
    if (items == null || items.isEmpty()) return pages;

    // 章の先頭ページ（見出し＋ボタン）
    pages.add(buildChapterHeader(title, color, items.size()));

    // 本文ページ構築（1ページに収める）
    StringBuilder current = new StringBuilder();
    for (String it : items) {
      if (it == null) continue;

      String cleaned = cleanEntry(it);
      if (cleaned.isEmpty()) continue;

      String entry = cleaned + "\n\n";

      if (current.length() + entry.length() > PAGE_CHAR_LIMIT) {
        pages.add(pageText(current.toString()));
        current = new StringBuilder();
      }

      if (entry.length() > PAGE_CHAR_LIMIT) {
        String cut = entry.substring(0, Math.min(entry.length(), PAGE_CHAR_LIMIT - 5)) + "...";
        current.append(cut).append("\n\n");
      } else {
        current.append(entry);
      }
    }

    if (current.length() > 0) {
      pages.add(pageText(current.toString()));
    }

    return pages;
  }

  private BaseComponent[] buildChapterHeader(String title, ChatColor color, int count) {
    List<BaseComponent> out = new ArrayList<>();

    // ✅ SUCCESS章は強く、TIME_UP章は詩っぽく寄せる（B路線）
    if ("SUCCESS".equalsIgnoreCase(title)) {
      out.add(line("◆ SUCCESS ◆", ChatColor.GREEN, true));
      out.add(line("Victory Records", ChatColor.GRAY, false));
    } else if ("TIME_UP".equalsIgnoreCase(title)) {
      out.add(line("◆ TIME_UP ◆", ChatColor.GOLD, true));
      out.add(line("The Unfinished Echo", ChatColor.GRAY, false));
    } else {
      out.add(line("◆ " + title + " ◆", color, true));
      out.add(line("Collected Quotes", ChatColor.GRAY, false));
    }

    out.add(new TextComponent("\n"));
    out.add(line("Count: " + count, ChatColor.WHITE, false));
    out.add(new TextComponent("\n"));

    out.add(button("▶ Back to TOC", "/quoteFavorite book toc", ChatColor.AQUA));
    out.add(button("▶ REREAD RANDOM (BOOK)", "/quoteFavorite reread book", ChatColor.LIGHT_PURPLE));

    return out.toArray(new BaseComponent[0]);
  }

  // =======================================================
  // ✅ 1ページをプレーン本文で作る
  // =======================================================
  private BaseComponent[] pageText(String body) {
    List<BaseComponent> out = new ArrayList<>();
    String text = (body == null) ? "" : body.trim();
    if (text.isEmpty()) text = "(empty)";

    // 本文は読みやすさ優先で色は薄く
    out.add(new TextComponent(ChatColor.WHITE + fitToPage(text)));
    return out.toArray(new BaseComponent[0]);
  }

  // =======================================================
  // ✅ パーツ生成
  // =======================================================
  private TextComponent line(String text, ChatColor color, boolean bold) {
    TextComponent c = new TextComponent((text == null ? "" : text) + "\n");
    c.setColor(color);
    c.setBold(bold);
    return c;
  }

  private TextComponent button(String label, String command, ChatColor color) {
    TextComponent c = new TextComponent(label + "\n");
    c.setColor(color);
    c.setBold(true);

    c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
    c.setHoverEvent(new HoverEvent(
        HoverEvent.Action.SHOW_TEXT,
        new BaseComponent[]{ new TextComponent(ChatColor.GRAY + "Click to run: " + command) }
    ));
    return c;
  }

  private String cleanEntry(String raw) {
    String t = raw.trim();
    if (t.isEmpty()) return "";

    // 色コードを外して図鑑テキストにする
    t = org.bukkit.ChatColor.stripColor(t);
    if (t == null) return "";

    t = t.replace("\r\n", "\n");
    while (t.contains("\n\n\n")) {
      t = t.replace("\n\n\n", "\n\n");
    }
    return t.trim();
  }

  private String fitToPage(String s) {
    if (s == null) return "";
    if (s.length() <= PAGE_CHAR_LIMIT) return s;
    return s.substring(0, PAGE_CHAR_LIMIT - 3) + "...";
  }
}