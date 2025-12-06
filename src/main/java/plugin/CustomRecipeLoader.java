package plugin;
// パッケージ名は実際のプラグインに合わせてください
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

public class CustomRecipeLoader {

  private final TreasureRunMultiChestPlugin plugin;

  public CustomRecipeLoader(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  public void registerRecipes() {
    registerSpecialEmeraldRecipe();
    registerGoldenAppleCustomRecipe();
    registerSpecialIronBlockRecipe();
  }

  /**
   * サンプル1: ダイヤ3個で特製エメラルド
   */
  private void registerSpecialEmeraldRecipe() {
    // ★ 完成品を new ItemStack で作らず、工場（TreasureItemFactory）経由で作る
    ItemStack result = plugin.getItemFactory().createTreasureEmerald(1);

    NamespacedKey key = new NamespacedKey(plugin, "special_emerald_recipe");
    ShapedRecipe recipe = new ShapedRecipe(key, result);

    recipe.shape("DDD", " D ", "DDD");
    recipe.setIngredient('D', Material.DIAMOND);

    Bukkit.addRecipe(recipe);
    plugin.getLogger().info("特製エメラルドのレシピを登録しました。");
  }

  /**
   * サンプル2: 金3個で特製リンゴ
   */
  private void registerGoldenAppleCustomRecipe() {
    ItemStack result = new ItemStack(Material.APPLE, 1);
    ItemMeta meta = result.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e特製リンゴ"));
      result.setItemMeta(meta);
    }

    NamespacedKey key = new NamespacedKey(plugin, "golden_apple_custom_recipe");
    ShapelessRecipe recipe = new ShapelessRecipe(key, result);

    recipe.addIngredient(3, Material.GOLD_INGOT);

    Bukkit.addRecipe(recipe);
    plugin.getLogger().info("特製リンゴのレシピを登録しました。");
  }

  /**
   * サンプル3: 鉄3個で特製鉄ブロック
   */
  private void registerSpecialIronBlockRecipe() {
    ItemStack result = new ItemStack(Material.IRON_BLOCK, 1);
    ItemMeta meta = result.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7特製鉄ブロック"));
      result.setItemMeta(meta);
    }

    NamespacedKey key = new NamespacedKey(plugin, "special_iron_block_recipe");
    ShapedRecipe recipe = new ShapedRecipe(key, result);

    recipe.shape("III", "III", "III");
    recipe.setIngredient('I', Material.IRON_INGOT);

    Bukkit.addRecipe(recipe);
    plugin.getLogger().info("特製鉄ブロックのレシピを登録しました。");
  }
}