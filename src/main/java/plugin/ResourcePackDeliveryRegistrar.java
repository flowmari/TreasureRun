package plugin;

/** Registers at most one join-time ResourcePack delivery path. */
final class ResourcePackDeliveryRegistrar {

  private ResourcePackDeliveryRegistrar() {
  }

  static ResourcePackFallbackService register(TreasureRunMultiChestPlugin plugin) {
    boolean standardEnabled = plugin.getConfig().getBoolean("resourcePack.enabled", false);
    boolean fallbackEnabled = plugin.getConfig().getBoolean("resourcePackFallback.enabled", false);
    ResourcePackDeliveryMode mode = ResourcePackDeliveryMode.select(
        standardEnabled,
        fallbackEnabled
    );

    if (standardEnabled && fallbackEnabled) {
      plugin.getLogger().warning(
          "[ResourcePack] both delivery paths are enabled; using standard delivery only."
      );
    }

    try {
      return switch (mode) {
        case STANDARD -> {
          plugin.getServer().getPluginManager().registerEvents(
              new ResourcePackDeliveryListener(plugin),
              plugin
          );
          plugin.getServer().getPluginManager().registerEvents(
              new ResourcePackStatusAuditListener(plugin),
              plugin
          );
          plugin.getLogger().info("[ResourcePack] standard delivery mode registered");
          yield null;
        }
        case FALLBACK -> {
          ResourcePackFallbackService service = new ResourcePackFallbackService(plugin);
          plugin.getServer().getPluginManager().registerEvents(
              new ResourcePackFallbackJoinListener(plugin),
              plugin
          );
          plugin.getServer().getPluginManager().registerEvents(
              new ResourcePackStatusAuditListener(plugin),
              plugin
          );
          plugin.getLogger().info("[ResourcePack] fallback delivery mode registered");
          yield service;
        }
        case DISABLED -> {
          plugin.getLogger().info("[ResourcePack] delivery disabled");
          yield null;
        }
      };
    } catch (Throwable t) {
      plugin.getLogger().warning(
          "[ResourcePack] delivery registration failed: " + t.getMessage()
      );
      return null;
    }
  }
}
