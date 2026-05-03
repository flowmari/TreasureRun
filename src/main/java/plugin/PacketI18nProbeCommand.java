package plugin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Development-only probe command for PacketI18n.
 *
 * Purpose:
 * - Send known Minecraft translatable components to the player.
 * - Let ProtocolLib PacketI18n audit confirm whether server-to-client JSON contains "translate".
 *
 * This is not gameplay logic.
 * It is a verification tool for packet-level i18n expansion.
 */
public final class PacketI18nProbeCommand implements CommandExecutor {

  private final TreasureRunMultiChestPlugin plugin;

  public PacketI18nProbeCommand(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("This command can only be used by a player.");
      return true;
    }

    if (!player.isOp()) {
      player.sendMessage(ChatColor.RED + "OP only.");
      return true;
    }

    player.sendMessage(ChatColor.YELLOW + "[PacketI18nProbe] sending translatable component samples...");

    send(player, "multiplayer.player.joined", player.getName());
    send(player, "multiplayer.player.left", player.getName());
    send(player, "death.attack.generic", player.getName());
    send(player, "death.attack.outOfWorld", player.getName());
    send(player, "chat.type.text", player.getName(), "hello from PacketI18n probe");
    send(player, "commands.help.header", "PacketI18n");

    player.sendMessage(ChatColor.GREEN + "[PacketI18nProbe] done. Check docker logs for translate= or json={...\"translate\"...}");

    plugin.getLogger().info("[PacketI18nProbe] sent translatable component samples to " + player.getName());
    return true;
  }

  private void send(Player player, String key, String... args) {
    TranslatableComponent component = new TranslatableComponent(key);

    if (args != null) {
      for (String arg : args) {
        component.addWith(new TextComponent(arg));
      }
    }

    player.spigot().sendMessage(component);
  }
}
