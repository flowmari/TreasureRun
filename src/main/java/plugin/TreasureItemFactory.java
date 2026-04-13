package plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class TreasureItemFactory {

  private final TreasureRunMultiChestPlugin plugin;
  private final NamespacedKey treasureEmeraldKey;
  private final I18nHelper i18n;

  public TreasureItemFactory(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.treasureEmeraldKey = new NamespacedKey(plugin, "treasure_emerald");
    this.i18n = new I18nHelper(plugin);
  }

  public ItemStack createTreasureEmerald(int amount) {
    ItemStack item = new ItemStack(Material.EMERALD, amount);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', i18n.trDefault(
          "items.specialEmerald.displayName",
          "&6Special Emerald"
      )));

      List<String> lore = new ArrayList<>();
      lore.add(ChatColor.translateAlternateColorCodes('&', i18n.trDefault(
          "items.specialEmerald.loreCrafted",
          "&7Crafted in TreasureRun"
      )));
      lore.add(ChatColor.translateAlternateColorCodes('&', i18n.trDefault(
          "items.specialEmerald.loreSpecial",
          "&7A special emerald"
      )));
      meta.setLore(lore);

      meta.addEnchant(Enchantment.LUCK, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

      meta.getPersistentDataContainer().set(treasureEmeraldKey, PersistentDataType.BYTE, (byte) 1);

      item.setItemMeta(meta);
    }
    return item;
  }

  public boolean isTreasureEmerald(ItemStack item) {
    if (item == null || item.getType() != Material.EMERALD || !item.hasItemMeta()) return false;

    ItemMeta meta = item.getItemMeta();
    if (meta == null) return false;

    Byte value = meta.getPersistentDataContainer().get(treasureEmeraldKey, PersistentDataType.BYTE);
    return value != null && value == (byte) 1;
  }
}