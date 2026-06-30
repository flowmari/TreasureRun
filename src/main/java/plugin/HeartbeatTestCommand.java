package plugin;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class HeartbeatTestCommand implements CommandExecutor {

  private final JavaPlugin plugin;

  public HeartbeatTestCommand(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("This command can only be used in game.");
      return true;
    }

    if (!player.hasPermission("treasure.debug.heartbeat")) {
      player.sendMessage(ChatColor.RED + "You do not have permission to run this heartbeat test.");
      return true;
    }

    if (!plugin.getConfig().getBoolean("heartbeat.enabled", true)) {
      player.sendMessage(ChatColor.YELLOW + "Heartbeat cues are disabled: heartbeat.enabled=false");
      return true;
    }

    SoundCategory category = configuredCategory();
    float minVolume = Math.max(1.50f, clampAudibleVolume((float) plugin.getConfig().getDouble("heartbeat.minVolume", 1.50)));
    float maxVolume = Math.max(3.00f, clampAudibleVolume((float) plugin.getConfig().getDouble("heartbeat.maxVolume", 3.00)));
    float multiplier = Math.max(1.00f, Math.max(0.0f, (float) plugin.getConfig().getDouble("heartbeat.volumeMultiplier", 1.00)));
    float testVolume = clampAudibleVolume(Math.max(minVolume, maxVolume) * multiplier);

    player.sendMessage(ChatColor.AQUA + "Playing heartbeat test cue..."
        + ChatColor.GRAY + " category=" + category
        + ", volume=" + String.format(Locale.ROOT, "%.2f", testVolume));
    player.sendMessage(ChatColor.GRAY + "If you do not hear it, check Minecraft Options > Music & Sounds > Players.");

    for (int i = 0; i < 4; i++) {
      final long baseDelay = i * 10L;
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        if (!player.isOnline()) return;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, category, testVolume * 0.95f, 0.80f);
      }, baseDelay);

      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        if (!player.isOnline()) return;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, category, testVolume * 0.30f, 1.60f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, category, testVolume * 0.25f, 1.15f);
      }, baseDelay + 2L);
    }

    return true;
  }

  private SoundCategory configuredCategory() {
    String raw = plugin.getConfig().getString("heartbeat.soundCategory", SoundCategory.PLAYERS.name());
    if (raw == null || raw.isBlank()) return SoundCategory.PLAYERS;
    try {
      return SoundCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return SoundCategory.PLAYERS;
    }
  }

  private float configuredFloat(String path, float fallback) {
    return clamp01((float) plugin.getConfig().getDouble(path, fallback));
  }

  private float clampAudibleVolume(float value) {
    if (Float.isNaN(value) || Float.isInfinite(value)) return 1.0f;
    return Math.max(0.0f, Math.min(3.0f, value));
  }

  private float clamp01(float value) {
    return Math.max(0.0f, Math.min(1.0f, value));
  }
}
