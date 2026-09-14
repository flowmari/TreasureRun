package plugin.placeholder.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import plugin.placeholder.OptionalPlaceholderIntegration;
import plugin.rank.LeaderboardPlaceholderResolver;

import java.util.Objects;

/** The only TreasureRun production class allowed to import PlaceholderAPI types. */
public final class TreasureRunPlaceholderExpansion
        extends PlaceholderExpansion
        implements OptionalPlaceholderIntegration.Adapter {

    private final JavaPlugin plugin;
    private final LeaderboardPlaceholderResolver resolver;
    private boolean registered;

    public TreasureRunPlaceholderExpansion(
            JavaPlugin plugin,
            LeaderboardPlaceholderResolver resolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public String getIdentifier() { return "treasurerun"; }

    @Override
    public String getAuthor() {
        String authors = String.join(", ", plugin.getDescription().getAuthors());
        return authors.isBlank() ? plugin.getDescription().getName() : authors;
    }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // Player identity is deliberately unused. This is a pure memory snapshot lookup.
        return resolver.resolve(params);
    }

    @Override
    public boolean start() {
        if (registered) return true;
        registered = register();
        return registered;
    }

    @Override
    public void stop() {
        if (!registered) return;
        unregister();
        registered = false;
    }
}
