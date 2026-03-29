package plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * LanguageSelectGui (27 slots) - Multiplayer Safe
 * - LanguageStore#getAllowedLanguages() を元にGUIを動的生成（最大27件 / ページングなし）
 * - クリックされたスロット -> 言語 の対応は「プレイヤーごと」に保持（同時に複数人が開いても壊れない）
 */
public class LanguageSelectGui implements Listener {

  private final TreasureRunMultiChestPlugin plugin;
  private final LanguageStore store;

  private static final String GUI_TITLE = ChatColor.DARK_AQUA + "Language / 言語";

  private final Map<UUID, String> pendingDifficulty = new HashMap<>();
  private final Set<UUID> closingBySelect = new HashSet<>();
  private final Map<UUID, Map<Integer, String>> slotToLangByPlayer = new HashMap<>();

  // ✅ 追加：GUIの用途（gameStart用 / gameMenu用）
  private enum PendingAction { START_GAME, GAME_MENU }
  private final Map<UUID, PendingAction> pendingAction = new HashMap<>();

  public LanguageSelectGui(TreasureRunMultiChestPlugin plugin, LanguageStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  public void open(Player player, String difficulty) {
    UUID uuid = player.getUniqueId();
    pendingDifficulty.put(uuid, difficulty);
    pendingAction.put(uuid, PendingAction.START_GAME); // ✅ 追加

    Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

    Map<Integer, String> slotToLang = new HashMap<>();
    slotToLangByPlayer.put(uuid, slotToLang);

    List<String> langs = new ArrayList<>(store.getAllowedLanguages());
    if (langs.isEmpty()) langs.add(store.getDefaultLang());

    int count = Math.min(langs.size(), 27);
    for (int i = 0; i < count; i++) {
      String lang = langs.get(i);
      int slot = i;
      slotToLang.put(slot, lang);
      inv.setItem(slot, createLanguageItem(lang));
    }

    if (langs.size() > 27) {
      player.sendMessage(ChatColor.YELLOW + "許可言語が多いため、GUIには先頭 27 件のみ表示します。");
    }

    player.openInventory(inv);
  }

  // ✅ 追加：/gameMenu gui 用（言語選択後にゲーム開始せず、本を開く）
  public void openForGameMenu(Player player, String difficulty) {
    UUID uuid = player.getUniqueId();
    pendingDifficulty.put(uuid, difficulty);
    pendingAction.put(uuid, PendingAction.GAME_MENU);

    Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

    Map<Integer, String> slotToLang = new HashMap<>();
    slotToLangByPlayer.put(uuid, slotToLang);

    List<String> langs = new ArrayList<>(store.getAllowedLanguages());
    if (langs.isEmpty()) langs.add(store.getDefaultLang());

    int count = Math.min(langs.size(), 27);
    for (int i = 0; i < count; i++) {
      String lang = langs.get(i);
      int slot = i;
      slotToLang.put(slot, lang);
      inv.setItem(slot, createLanguageItem(lang));
    }

    if (langs.size() > 27) {
      player.sendMessage(ChatColor.YELLOW + "許可言語が多いため、GUIには先頭 27 件のみ表示します。");
    }

    player.openInventory(inv);
  }

  /**
   * ✅ store（config）完全依存のアイコン生成：
   * - displayName: language.displayName.<lang>（無ければ fallback）
   * - shortLabel : language.shortLabel.<lang>（無ければ fallback）
   * - iconMaterial: language.iconMaterial.<lang>（無ければ PAPER）
   * - lore: language.lore.<lang>（無ければ loreDefault）
   */
  private ItemStack createLanguageItem(String langRaw) {
    String lang = (langRaw == null) ? "" : langRaw.trim().toLowerCase(Locale.ROOT);

    // ★ここが最重要：GUIは “必ず store 経由”
    String langName = store.getDisplayName(lang);   // 例: Italiano
    String label = store.getShortLabel(lang);       // 例: IT
    Material mat = store.getIconMaterial(lang);     // 例: YELLOW_WOOL
    String loreLine = store.getLore(lang);          // 例: Clicca per scegliere（無ければ loreDefault）

    if (mat == null) mat = Material.PAPER;
    if (langName == null || langName.isBlank()) langName = lang;
    if (label == null || label.isBlank()) label = lang.toUpperCase(Locale.ROOT);
    if (loreLine == null || loreLine.isBlank()) loreLine = store.getLoreDefault();

    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      // 2枚目みたいに [IT] Italiano (it)
      meta.setDisplayName(ChatColor.AQUA + "[" + label + "] " + langName
          + ChatColor.DARK_GRAY + " (" + lang + ")");

      meta.setLore(Collections.singletonList(ChatColor.GRAY + loreLine));

      // defaultLang はキラッ
      if (lang.equalsIgnoreCase(store.getDefaultLang())) {
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      }

      item.setItemMeta(meta);
    }
    return item;
  }

  @EventHandler
  public void onClick(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player player)) return;
    if (!GUI_TITLE.equals(e.getView().getTitle())) return;

    e.setCancelled(true);

    UUID uuid = player.getUniqueId();
    Map<Integer, String> slotToLang = slotToLangByPlayer.get(uuid);
    if (slotToLang == null) return;

    int slot = e.getRawSlot();
    if (!slotToLang.containsKey(slot)) return;

    String lang = slotToLang.get(slot);

    store.set(uuid, lang);
    String diff = pendingDifficulty.getOrDefault(uuid, "Normal");

    PendingAction action = pendingAction.getOrDefault(uuid, PendingAction.START_GAME);

    closingBySelect.add(uuid);

    pendingDifficulty.remove(uuid);
    slotToLangByPlayer.remove(uuid);
    pendingAction.remove(uuid); // ✅ 追加

    player.closeInventory();

    if (action == PendingAction.GAME_MENU) {
      // ✅ gameMenu用：選んだ瞬間に「その言語で本を開く」（ゲーム開始しない）
      plugin.openGameMenuOnly(player, lang);
    } else {
      // ✅ 従来：言語確定 → gameStart
      plugin.beginGameStartAfterLanguageSelected(player, diff, lang);
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent e) {
    if (!(e.getPlayer() instanceof Player player)) return;
    if (!GUI_TITLE.equals(e.getView().getTitle())) return;

    UUID uuid = player.getUniqueId();

    // ✅ クリック確定で閉じた場合は何も出さない
    if (closingBySelect.remove(uuid)) return;

    PendingAction action = pendingAction.getOrDefault(uuid, PendingAction.START_GAME);

    if (action == PendingAction.GAME_MENU) {
      player.sendMessage(ChatColor.YELLOW + "言語選択をキャンセルしました。もう一度開く場合は "
          + ChatColor.AQUA + "/gameMenu gui" + ChatColor.YELLOW + " を実行してね。");
    } else {
      player.sendMessage(ChatColor.YELLOW + "言語選択をキャンセルしました。もう一度やる場合は "
          + ChatColor.AQUA + "/gameStart <easy|normal|hard>" + ChatColor.YELLOW
          + " または " + ChatColor.AQUA + "/gamestart <easy|normal|hard>" + ChatColor.YELLOW
          + " を実行してね。");
    }

    // ✅ キャンセルで閉じた場合も掃除（安全）
    pendingDifficulty.remove(uuid);
    slotToLangByPlayer.remove(uuid);
    pendingAction.remove(uuid);
  }
}