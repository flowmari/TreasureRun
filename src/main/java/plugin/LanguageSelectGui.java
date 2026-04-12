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

public class LanguageSelectGui implements Listener {

  private final TreasureRunMultiChestPlugin plugin;
  private final LanguageStore store;

  private final Map<UUID, String> pendingDifficulty = new HashMap<>();
  private final Set<UUID> closingBySelect = new HashSet<>();
  private final Map<UUID, Map<Integer, String>> slotToLangByPlayer = new HashMap<>();
  private final Map<UUID, String> openTitleByPlayer = new HashMap<>();

  private enum PendingAction { START_GAME, GAME_MENU }
  private final Map<UUID, PendingAction> pendingAction = new HashMap<>();

  public LanguageSelectGui(TreasureRunMultiChestPlugin plugin, LanguageStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  public void open(Player player, String difficulty) {
    openInternal(player, difficulty, PendingAction.START_GAME);
  }

  public void openForGameMenu(Player player, String difficulty) {
    openInternal(player, difficulty, PendingAction.GAME_MENU);
  }

  private void openInternal(Player player, String difficulty, PendingAction action) {
    UUID uuid = player.getUniqueId();

    pendingDifficulty.put(uuid, difficulty);
    pendingAction.put(uuid, action);

    String lang = resolvePlayerLang(player);
    String title = colorize(trByLang(lang, "gui.language.title"));
    openTitleByPlayer.put(uuid, title);

    Inventory inv = Bukkit.createInventory(null, 27, title);

    Map<Integer, String> slotToLang = new HashMap<>();
    slotToLangByPlayer.put(uuid, slotToLang);

    List<String> langs = new ArrayList<>(store.getAllowedLanguages());
    if (langs.isEmpty()) langs.add(store.getDefaultLang());

    int count = Math.min(langs.size(), 27);
    for (int i = 0; i < count; i++) {
      String selectableLang = langs.get(i);
      slotToLang.put(i, selectableLang);
      inv.setItem(i, createLanguageItem(selectableLang));
    }

    if (langs.size() > 27) {
      player.sendMessage(ChatColor.YELLOW + tr(player, "command.lang.guiTooManyShown"));
    }

    player.openInventory(inv);
  }

  private ItemStack createLanguageItem(String langRaw) {
    String lang = (langRaw == null) ? "" : langRaw.trim().toLowerCase(Locale.ROOT);

    String langName = store.getDisplayName(lang);
    String label = store.getShortLabel(lang);
    Material mat = store.getIconMaterial(lang);
    String loreLine = store.getLore(lang);

    if (mat == null) mat = Material.PAPER;
    if (langName == null || langName.isBlank()) langName = lang;
    if (label == null || label.isBlank()) label = lang.toUpperCase(Locale.ROOT);
    if (loreLine == null || loreLine.isBlank()) loreLine = store.getLoreDefault();

    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(ChatColor.AQUA + "[" + label + "] " + langName
          + ChatColor.DARK_GRAY + " (" + lang + ")");
      meta.setLore(Collections.singletonList(ChatColor.GRAY + loreLine));

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

    UUID uuid = player.getUniqueId();
    String expectedTitle = openTitleByPlayer.get(uuid);
    if (expectedTitle == null) return;
    if (!expectedTitle.equals(e.getView().getTitle())) return;

    e.setCancelled(true);

    Map<Integer, String> slotToLang = slotToLangByPlayer.get(uuid);
    if (slotToLang == null) return;

    int slot = e.getRawSlot();
    if (!slotToLang.containsKey(slot)) return;

    String selectedLang = slotToLang.get(slot);

    store.set(uuid, selectedLang);
    String diff = pendingDifficulty.getOrDefault(uuid, "Normal");
    PendingAction action = pendingAction.getOrDefault(uuid, PendingAction.START_GAME);

    closingBySelect.add(uuid);

    pendingDifficulty.remove(uuid);
    slotToLangByPlayer.remove(uuid);
    pendingAction.remove(uuid);
    openTitleByPlayer.remove(uuid);

    player.closeInventory();

    if (action == PendingAction.GAME_MENU) {
      plugin.openGameMenuOnly(player, selectedLang);
    } else {
      plugin.beginGameStartAfterLanguageSelected(player, diff, selectedLang);
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent e) {
    if (!(e.getPlayer() instanceof Player player)) return;

    UUID uuid = player.getUniqueId();
    String expectedTitle = openTitleByPlayer.get(uuid);
    if (expectedTitle == null) return;
    if (!expectedTitle.equals(e.getView().getTitle())) return;

    if (closingBySelect.remove(uuid)) return;

    PendingAction action = pendingAction.getOrDefault(uuid, PendingAction.START_GAME);

    if (action == PendingAction.GAME_MENU) {
      player.sendMessage(ChatColor.YELLOW + tr(player, "command.lang.guiCancelledGameMenu"));
    } else {
      player.sendMessage(ChatColor.YELLOW + tr(player, "command.lang.guiCancelledGameStart"));
    }

    pendingDifficulty.remove(uuid);
    slotToLangByPlayer.remove(uuid);
    pendingAction.remove(uuid);
    openTitleByPlayer.remove(uuid);
  }

  private String resolvePlayerLang(Player player) {
    String defaultLang = plugin.getConfig().getString("language.default", "ja");

    try {
      if (plugin.getPlayerLanguageStore() != null) {
        String saved = plugin.getPlayerLanguageStore().getLang(player.getUniqueId(), "");
        if (saved != null && !saved.isBlank()) return saved;
      }
    } catch (Throwable ignored) {}

    return defaultLang;
  }

  private String tr(Player player, String key) {
    return trByLang(resolvePlayerLang(player), key);
  }

  private String trByLang(String lang, String key) {
    String s = plugin.getI18n().tr(lang, key);
    if (isUsable(s)) return s;

    String defaultLang = plugin.getConfig().getString("language.default", "ja");
    s = plugin.getI18n().tr(defaultLang, key);
    if (isUsable(s)) return s;

    s = plugin.getI18n().tr("en", key);
    if (isUsable(s)) return s;

    return key;
  }

  private boolean isUsable(String s) {
    return s != null && !s.isBlank() && !s.contains("Translation missing:");
  }

  private String colorize(String s) {
    if (s == null) return "";
    return ChatColor.translateAlternateColorCodes('&', s);
  }
}
