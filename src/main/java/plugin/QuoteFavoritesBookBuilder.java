package plugin;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Bridge class.
 *
 * plugin.QuoteFavoriteBookClickListener expects plugin.QuoteFavoritesBookBuilder,
 * but the real implementation lives in plugin.quote.QuoteFavoritesBookBuilder.
 *
 * This class delegates calls to the real builder.
 */
public class QuoteFavoritesBookBuilder {

  private final TreasureRunMultiChestPlugin plugin;
  private final plugin.quote.QuoteFavoritesBookBuilder delegate;

  public QuoteFavoritesBookBuilder(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
    this.delegate = new plugin.quote.QuoteFavoritesBookBuilder(resolveI18n(plugin));
  }

  public ItemStack buildEmptyFavoritesBook(Player player) {
    return buildFavoritesBook(player, Collections.emptyList());
  }

  public ItemStack buildFavoritesBook(Player player, List<String> rows) {
    if (player == null) {
      return delegate.buildFavoritesBook(
          "en",
          new java.util.UUID(0L, 0L),
          0,
          (rows == null ? Collections.emptyList() : rows)
      );
    }

    String lang = resolvePlayerLang(player);
    int count = (rows == null ? 0 : rows.size());

    return delegate.buildFavoritesBook(
        lang,
        player.getUniqueId(),
        count,
        (rows == null ? Collections.emptyList() : rows)
    );
  }

  private static I18n resolveI18n(TreasureRunMultiChestPlugin plugin) {
    if (plugin == null) return null;

    try {
      java.lang.reflect.Method m = plugin.getClass().getMethod("getI18n");
      Object v = m.invoke(plugin);
      if (v instanceof I18n i) return i;
    } catch (Throwable ignored) {}

    try {
      java.lang.reflect.Field f = plugin.getClass().getDeclaredField("i18n");
      f.setAccessible(true);
      Object v = f.get(plugin);
      if (v instanceof I18n i) return i;
    } catch (Throwable ignored) {}

    try {
      I18n i = new I18n(plugin);
      i.loadOrCreate();
      return i;
    } catch (Throwable ignored) {}

    return null;
  }

  private String resolvePlayerLang(Player player) {
    if (plugin == null || player == null) return "en";

    try {
      if (plugin.getPlayerLanguageStore() != null) {
        String saved = plugin.getPlayerLanguageStore().getLang(player.getUniqueId(), "");
        if (saved != null && !saved.isBlank()) return saved;
      }
    } catch (Throwable ignored) {}

    try {
      return plugin.getConfig().getString("language.default", "ja");
    } catch (Throwable ignored) {
      return "en";
    }
  }
}
