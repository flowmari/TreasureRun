package plugin;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Bridge class.
 *
 * plugin.QuoteFavoriteBookClickListener expects plugin.QuoteFavoritesBookBuilder,
 * but the real implementation lives in plugin.quote.QuoteFavoritesBookBuilder.
 *
 * This class delegates calls to the real builder.
 */
public class QuoteFavoritesBookBuilder {

  private final plugin.quote.QuoteFavoritesBookBuilder delegate;

  public QuoteFavoritesBookBuilder() {
    this.delegate = new plugin.quote.QuoteFavoritesBookBuilder();
  }

  // =======================================================
  // ✅ methods expected by QuoteFavoriteBookClickListener
  // =======================================================

  public ItemStack buildEmptyFavoritesBook(Player player) {
    return buildFavoritesBook(player, Collections.emptyList());
  }

  public ItemStack buildFavoritesBook(Player player, List<String> rows) {
    if (player == null) {
      // safest fallback
      return delegate.buildFavoritesBook("en", new java.util.UUID(0L, 0L), 0,
          (rows == null ? Collections.emptyList() : rows));
    }

    String lang = "en";
    try {
      // If the real builder has playerLanguageStore, use it (optional)
      java.lang.reflect.Field f = delegate.getClass().getDeclaredField("playerLanguageStore");
      f.setAccessible(true);
      Object pls = f.get(delegate);
      if (pls != null) {
        java.lang.reflect.Method m = pls.getClass().getMethod("getPlayerLang", java.util.UUID.class);
        Object r = m.invoke(pls, player.getUniqueId());
        if (r != null) lang = String.valueOf(r);
      }
    } catch (Throwable ignored) {}

    int count = (rows == null ? 0 : rows.size());
    return delegate.buildFavoritesBook(lang, player.getUniqueId(), count,
        (rows == null ? Collections.emptyList() : rows));
  }
}
