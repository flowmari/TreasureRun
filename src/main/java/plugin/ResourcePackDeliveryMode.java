package plugin;

/** Selects one ResourcePack delivery path for a server runtime. */
enum ResourcePackDeliveryMode {
  DISABLED,
  STANDARD,
  FALLBACK;

  static ResourcePackDeliveryMode select(boolean standardEnabled, boolean fallbackEnabled) {
    if (standardEnabled) {
      return STANDARD;
    }
    if (fallbackEnabled) {
      return FALLBACK;
    }
    return DISABLED;
  }
}
