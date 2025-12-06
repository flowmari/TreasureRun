package plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class TreasureItemFactory {

  private final JavaPlugin plugin;
  private final NamespacedKey treasureEmeraldKey;

  public TreasureItemFactory(JavaPlugin plugin) {
    this.plugin = plugin;
    this.treasureEmeraldKey = new NamespacedKey(plugin, "treasure_emerald");
  }

  public ItemStack createTreasureEmerald(int amount) {
    ItemStack item = new ItemStack(Material.EMERALD, amount);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(ChatColor.GOLD + "特製エメラルド");

      List<String> lore = new ArrayList<>();
      lore.add(ChatColor.GRAY + "TreasureRunでクラフトした");
      lore.add(ChatColor.GRAY + "特別なエメラルド");
      meta.setLore(lore);

      // 見た目演出（任意）
      meta.addEnchant(Enchantment.LUCK, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

      // ★確実判別タグ（PDC）
      meta.getPersistentDataContainer().set(treasureEmeraldKey, PersistentDataType.BYTE, (byte) 1);

      item.setItemMeta(meta);
    }
    return item;
  }

  public boolean isTreasureEmerald(ItemStack item) {
    if (item == null || item.getType() != Material.EMERALD) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return false;

    Byte flag = meta.getPersistentDataContainer().get(treasureEmeraldKey, PersistentDataType.BYTE);
    return flag != null && flag.byteValue() == 1;
  }
}