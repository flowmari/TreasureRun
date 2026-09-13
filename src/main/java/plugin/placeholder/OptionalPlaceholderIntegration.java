package plugin.placeholder;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import plugin.DatabaseRuntimeSettings;
import plugin.rank.JdbcLeaderboardSnapshotLoader;
import plugin.rank.LeaderboardPlaceholderResolver;
import plugin.rank.LeaderboardSnapshotCache;
import plugin.rank.LeaderboardSnapshotRefresher;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * PlaceholderAPI-neutral bootstrap. Provider-specific code is loaded by name only after the
 * optional provider is confirmed present and enabled.
 */
public final class OptionalPlaceholderIntegration implements AutoCloseable {

    private static final String PROVIDER_NAME = "PlaceholderAPI";
    private static final String ADAPTER_CLASS =
            "plugin.placeholder.papi.TreasureRunPlaceholderExpansion";
    private static final long INITIAL_DELAY_TICKS = 1L;
    private static final long REFRESH_PERIOD_TICKS = 20L * 30L;

    public interface Adapter {
        boolean start();
        void stop();
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private final DatabaseRuntimeSettings databaseSettings;
    private final LeaderboardSnapshotCache cache = new LeaderboardSnapshotCache();
    private final LeaderboardPlaceholderResolver resolver =
            new LeaderboardPlaceholderResolver(cache);

    private LeaderboardSnapshotRefresher refresher;
    private BukkitTask refreshTask;
    private Adapter adapter;
    private boolean started;

    public OptionalPlaceholderIntegration(
            JavaPlugin plugin,
            DatabaseRuntimeSettings databaseSettings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = this.plugin.getLogger();
        this.databaseSettings = Objects.requireNonNull(databaseSettings, "databaseSettings");
    }

    public void start() {
        if (started) return;
        started = true;

        Plugin provider = plugin.getServer().getPluginManager().getPlugin(PROVIDER_NAME);
        if (provider == null || !provider.isEnabled()) {
            plugin.getLogger().info(
                    "[LeaderboardPlaceholders] PlaceholderAPI is not installed; "
                            + "optional placeholders are disabled.");
            return;
        }

        Adapter candidate;
        try {
            candidate = loadProviderAdapter();
            if (!candidate.start()) {
                plugin.getLogger().warning(
                        "[LeaderboardPlaceholders] PlaceholderAPI expansion registration "
                                + "was rejected; optional placeholders remain disabled.");
                return;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().warning(
                    "[LeaderboardPlaceholders] Could not register optional PlaceholderAPI "
                            + "integration: "
                            + exception.getMessage());
            return;
        }
        adapter = candidate;

        if (!databaseSettings.enabled()) {
            plugin.getLogger().info(
                    "[LeaderboardPlaceholders] database.enabled=false; placeholders are "
                            + "registered and return neutral empty leaderboard values.");
            return;
        }

        refresher =
                new LeaderboardSnapshotRefresher(
                        cache,
                        new JdbcLeaderboardSnapshotLoader(databaseSettings),
                        exception ->
                                logger.warning(
                                        "[LeaderboardPlaceholders] ranking refresh failed; "
                                                + "keeping the last successful snapshot: "
                                                + exception.getMessage()));

        refreshTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                refresher::refresh,
                                INITIAL_DELAY_TICKS,
                                REFRESH_PERIOD_TICKS);

        plugin.getLogger().info(
                "[LeaderboardPlaceholders] optional integration enabled; leaderboard refresh "
                        + "runs asynchronously about every 30 seconds.");
    }

    private Adapter loadProviderAdapter() throws ReflectiveOperationException {
        Class<?> type =
                Class.forName(ADAPTER_CLASS, true, plugin.getClass().getClassLoader());
        Constructor<?> constructor =
                type.getConstructor(JavaPlugin.class, LeaderboardPlaceholderResolver.class);
        Object instance = constructor.newInstance(plugin, resolver);
        if (!(instance instanceof Adapter loadedAdapter)) {
            throw new IllegalStateException(
                    ADAPTER_CLASS + " does not implement the neutral adapter contract");
        }
        return loadedAdapter;
    }

    @Override
    public void close() {
        LeaderboardSnapshotRefresher currentRefresher = refresher;
        refresher = null;
        if (currentRefresher != null) {
            // cancel() does not prove an already-running JDBC operation has stopped. Disable
            // publication first so a late query result cannot replace the snapshot.
            currentRefresher.close();
        }

        BukkitTask currentTask = refreshTask;
        refreshTask = null;
        if (currentTask != null) currentTask.cancel();

        Adapter currentAdapter = adapter;
        adapter = null;
        if (currentAdapter != null) {
            try {
                currentAdapter.stop();
            } catch (LinkageError | RuntimeException exception) {
                plugin.getLogger().warning(
                        "[LeaderboardPlaceholders] expansion unregister failed: "
                                + exception.getMessage());
            }
        }
        started = false;
    }
}
