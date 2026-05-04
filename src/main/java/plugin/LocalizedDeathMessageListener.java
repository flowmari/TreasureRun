package plugin;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class LocalizedDeathMessageListener implements Listener {

  private final TreasureRunMultiChestPlugin plugin;

  public LocalizedDeathMessageListener(TreasureRunMultiChestPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    if (player == null) return;
    String lang = resolvePlayerLang(player);
    String key = resolveDeathKey(event);
    String killerName = resolveKillerName(event);
    String message = plugin.getI18n().tr(lang, key,
        I18n.Placeholder.of("{player}", player.getName()),
        I18n.Placeholder.of("{arg0}", player.getName()),
        I18n.Placeholder.of("{arg1}", killerName));
    if (!valid(message)) {
      message = plugin.getI18n().tr(lang, "gameplay.death.generic",
          I18n.Placeholder.of("{player}", player.getName()));
    }
    if (valid(message)) event.setDeathMessage(message);
  }

  private String resolveDeathKey(PlayerDeathEvent event) {
    EntityDamageEvent last = null;
    try { last = event.getEntity().getLastDamageCause(); } catch (Throwable ignored) {}
    EntityDamageEvent.DamageCause cause = last != null
        ? last.getCause() : EntityDamageEvent.DamageCause.CUSTOM;
    boolean byEntity = last instanceof EntityDamageByEntityEvent;
    switch (cause) {
      case FALL: return byEntity ? "minecraft.packet.death.fell.finish" : "minecraft.packet.death.fell.accident.generic";
      case FIRE: return byEntity ? "minecraft.packet.death.attack.inFire.player" : "minecraft.packet.death.attack.inFire._value";
      case FIRE_TICK: return byEntity ? "minecraft.packet.death.attack.onFire.player" : "minecraft.packet.death.attack.onFire._value";
      case LAVA: return byEntity ? "minecraft.packet.death.attack.lava.player" : "minecraft.packet.death.attack.lava._value";
      case DROWNING: return byEntity ? "minecraft.packet.death.attack.drown.player" : "minecraft.packet.death.attack.drown._value";
      case SUFFOCATION: return byEntity ? "minecraft.packet.death.attack.inWall.player" : "minecraft.packet.death.attack.inWall._value";
      case STARVATION: return byEntity ? "minecraft.packet.death.attack.starve.player" : "minecraft.packet.death.attack.starve._value";
      case POISON: return byEntity ? "minecraft.packet.death.attack.magic.player" : "minecraft.packet.death.attack.magic._value";
      case MAGIC: return byEntity ? "minecraft.packet.death.attack.indirectMagic._value" : "minecraft.packet.death.attack.even_more_magic";
      case WITHER: return byEntity ? "minecraft.packet.death.attack.wither.player" : "minecraft.packet.death.attack.wither._value";
      case BLOCK_EXPLOSION: return "minecraft.packet.death.attack.explosion._value";
      case ENTITY_EXPLOSION: return byEntity ? "minecraft.packet.death.attack.explosion.player._value" : "minecraft.packet.death.attack.explosion._value";
      case PROJECTILE: return "minecraft.packet.death.attack.arrow._value";
      case ENTITY_ATTACK: case ENTITY_SWEEP_ATTACK: return byEntity ? "minecraft.packet.death.attack.mob._value" : "minecraft.packet.death.attack.generic._value";
      case VOID: return byEntity ? "minecraft.packet.death.attack.outOfWorld.player" : "minecraft.packet.death.attack.outOfWorld._value";
      case LIGHTNING: return byEntity ? "minecraft.packet.death.attack.lightningBolt.player" : "minecraft.packet.death.attack.lightningBolt._value";
      case CRAMMING: return byEntity ? "minecraft.packet.death.attack.cramming.player" : "minecraft.packet.death.attack.cramming._value";
      case HOT_FLOOR: return byEntity ? "minecraft.packet.death.attack.hotFloor.player" : "minecraft.packet.death.attack.hotFloor._value";
      case DRAGON_BREATH: return byEntity ? "minecraft.packet.death.attack.dragonBreath.player" : "minecraft.packet.death.attack.dragonBreath._value";
      case FREEZE: return byEntity ? "minecraft.packet.death.attack.freeze.player" : "minecraft.packet.death.attack.freeze._value";
      case SONIC_BOOM: return byEntity ? "minecraft.packet.death.attack.sonic_boom.player" : "minecraft.packet.death.attack.sonic_boom._value";
      case DRYOUT: return byEntity ? "minecraft.packet.death.attack.dryout.player" : "minecraft.packet.death.attack.dryout._value";
      case FLY_INTO_WALL: return byEntity ? "minecraft.packet.death.attack.flyIntoWall.player" : "minecraft.packet.death.attack.flyIntoWall._value";
      case THORNS: return "minecraft.packet.death.attack.thorns._value";
      case FALLING_BLOCK: return byEntity ? "minecraft.packet.death.attack.fallingBlock.player" : "minecraft.packet.death.attack.fallingBlock._value";
      case KILL: return "minecraft.packet.death.attack.genericKill._value";
      default:
        String raw = event.getDeathMessage();
        if (raw != null && raw.toLowerCase(java.util.Locale.ROOT).contains("firework"))
          return "gameplay.death.firework";
        return "minecraft.packet.death.attack.generic._value";
    }
  }

  private String resolveKillerName(PlayerDeathEvent event) {
    try {
      EntityDamageEvent last = event.getEntity().getLastDamageCause();
      if (last instanceof EntityDamageByEntityEvent byEntity) {
        Entity d = byEntity.getDamager();
        if (d instanceof Player p) return p.getName();
        if (d != null) return d.getType().name().replace("_", " ");
      }
    } catch (Throwable ignored) {}
    return "";
  }

  private String resolvePlayerLang(Player player) {
    String def = "ja";
    try { def = plugin.getConfig().getString("language.default", "ja"); } catch (Throwable ignored) {}
    try { if (plugin.getPlayerLanguageStore() != null) return plugin.getPlayerLanguageStore().getLang(player, def); } catch (Throwable ignored) {}
    return def;
  }

  private boolean valid(String s) {
    return s != null && !s.isBlank() && !s.contains("Translation missing:");
  }
}
