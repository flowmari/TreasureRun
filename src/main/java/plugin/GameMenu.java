package plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * ゲーム開始時に表示する「目次（ルール説明）」を担当するクラス
 * ・showGameMenu(...)  : チャットに短い要約を1ブロックで表示
 * ・openRuleBook(...) : 本(WRITTEN_BOOK)のUIで詳しい説明を表示
 */
public class GameMenu {

  /**
   * チャットに「短い要約メニュー（1〜6）」を1ブロックで表示する
   */
  public static void showGameMenu(Player player, String difficulty) {
    player.sendMessage(
        ChatColor.GOLD + "===== 🌟 TreasureRun 目次 🌟 =====\n" +
            ChatColor.AQUA + "1. ゲームの目的\n" +
            ChatColor.WHITE + "   ・制限時間内にできるだけ多くの宝箱を開けて、スコアを稼ぎます。\n\n" +

            ChatColor.AQUA + "2. 現在の難易度\n" +
            ChatColor.WHITE + "   ・今の難易度: " + ChatColor.YELLOW + difficulty + "\n\n" +

            ChatColor.AQUA + "3. 難易度と色\n" +
            ChatColor.WHITE + "   ・Easy  ：紫のブロック\n" +
            ChatColor.WHITE + "   ・Normal：緑のブロック\n" +
            ChatColor.WHITE + "   ・Hard  ：青のブロック\n\n" +

            ChatColor.AQUA + "4. 操作方法\n" +
            ChatColor.WHITE + "   ・周りを走り回って、宝箱を見つけて右クリックで開けます。\n\n" +

            ChatColor.AQUA + "5. スコア\n" +
            ChatColor.WHITE + "   ・普通の宝物で +100 点\n" +
            ChatColor.WHITE + "   ・特別な宝物（ネザライト、ブロックなど）でさらにボーナス点！\n\n" +

            ChatColor.AQUA + "6. 終了条件\n" +
            ChatColor.WHITE + "   ・すべての宝箱を開けるか、時間切れでゲーム終了です。\n" +
            ChatColor.GOLD + "=================================="
    );
  }

  /**
   * 本(WRITTEN_BOOK)のUIで、詳しいルール説明を表示する
   * ゲーム開始時や /gameMenu で呼び出す想定
   */
  public static void openRuleBook(Player player, String difficulty) {

    // ルールブック（書見台付きの本）を作成
    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) book.getItemMeta();

    if (meta == null) {
      player.sendMessage(ChatColor.RED + "ルールブックを開けませんでした。");
      return;
    }

    meta.setTitle("TreasureRun ルール");
    meta.setAuthor("TreasureRun");

    String diffJP = switch (difficulty) {
      case "Easy" -> "Easy（ゆったり）";
      case "Hard" -> "Hard（高難度）";
      default -> "Normal（標準）";
    };

    List<String> pages = new ArrayList<>();

    // 1ページ目（タイトル：蛍光ブルー＋太字、本文：ブルーブラック）
    pages.add(
        ChatColor.AQUA + "" + ChatColor.BOLD + "TreasureRun ルール\n\n" +
            ChatColor.DARK_BLUE +
            "難易度: " + diffJP + "\n\n" +
            "制限時間内にできるだけ多くの\n" +
            "宝箱を開けよう！\n" +
            "レアな宝物ほど高得点です。"
    );

    // 2ページ目（見出し：AQUA、本文：DARK_BLUE）
    pages.add(
        ChatColor.AQUA + "★ 基本の流れ\n\n" +
            ChatColor.DARK_BLUE +
            "1. /gameStart <難易度>\n" +
            "2. 緑のマークの宝箱を探す\n" +
            "3. 開けるとスコア + アイテム\n" +
            "4. 全て開けるとクリア！"
    );

    // 3ページ目（見出し：AQUA、本文：DARK_BLUE）
    pages.add(
        ChatColor.AQUA + "★ ヒント\n\n" +
            ChatColor.DARK_BLUE +
            "・ネザライト/ブロック系は\n" +
            "  ジャックポット高得点！\n\n" +
            "・途中で /gameMenu を打つと\n" +
            "  この本を再取得できます。\n\n" +
            "・タイムアップに注意！"
    );

    meta.setPages(pages);
    book.setItemMeta(meta);

    // ホットバーに入れるための表示名を付ける（タイトルも蛍光ブルー系に）
    ItemMeta displayMeta = book.getItemMeta();
    displayMeta.setDisplayName(ChatColor.AQUA + "TreasureRun ルールブック");
    book.setItemMeta(displayMeta);

    PlayerInventory inv = player.getInventory();

    // 既に同じ名前の本があれば削除（重複防止）
    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack item = inv.getItem(i);
      if (item == null) continue;
      if (item.getType() != Material.WRITTEN_BOOK) continue;
      if (!item.hasItemMeta()) continue;
      ItemMeta im = item.getItemMeta();
      if (!im.hasDisplayName()) continue;

      String name = ChatColor.stripColor(im.getDisplayName());
      if ("TreasureRun ルールブック".equals(name)) {
        inv.clear(i);
      }
    }

    // ホットバーの一番左（スロット0）に入れる
    inv.setItem(0, book);
    player.updateInventory();

    // 手に持たせてすぐ開く
    player.getInventory().setHeldItemSlot(0);
    player.openBook(book);

    player.sendMessage(ChatColor.GOLD + "📖 ルールブックをホットバーに配布しました。");
    player.sendMessage(ChatColor.YELLOW + "手に持って右クリックすると、いつでも読み直せます。");
  }
}