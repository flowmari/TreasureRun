/**
 * Pure i18n support package.
 *
 * <p>Boundary rule:</p>
 *
 * <ul>
 *   <li>No Bukkit imports</li>
 *   <li>No ProtocolLib imports</li>
 *   <li>No Fabric imports</li>
 *   <li>No Minecraft runtime imports</li>
 * </ul>
 *
 * <p>
 * Minecraft / ProtocolLib / Bukkit / Player access must stay in adapter or boundary classes.
 * This package should contain platform-free localization logic that can be tested without
 * starting a Minecraft server.
 * </p>
 */
package plugin.i18n;
