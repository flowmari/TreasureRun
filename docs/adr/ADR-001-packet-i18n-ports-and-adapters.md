# ADR-001: Packet i18n Ports and Adapters Boundary

## Status

Accepted

## Context

Spigot alone cannot fully control Minecraft standard UI text.

TreasureRun uses multiple layers to support Minecraft UI localization:

- ProtocolLib packet boundary layer
- ResourcePack language assets
- Fabric runtime synchronization
- pure Java PacketI18n JSON localizer

The main design risk is mixing Minecraft / Bukkit / ProtocolLib runtime APIs directly into localization logic. If this happens, the i18n logic becomes difficult to test without starting a Minecraft server.

## Decision

TreasureRun separates platform-dependent boundary code from platform-free localization logic.

- ProtocolLib listener classes remain responsible for packet access and runtime player context.
- ResourcePack and Fabric layers remain responsible for client-side language assets and synchronization.
- `plugin.i18n` contains platform-free localization logic.
- `PacketI18nJsonLocalizer` performs JSON localization without depending on Bukkit, ProtocolLib, Fabric, or Minecraft runtime APIs.
- Platform access must be passed into pure logic as plain values, such as locale strings and translator callbacks.

## Consequences

### Positive

- Packet JSON localization can be tested without starting a Minecraft server.
- Bukkit / ProtocolLib dependencies stay at the adapter boundary.
- The pure i18n package can be protected by an architectural fitness function.
- Future refactors are easier because the boundary rule is explicit.

### Trade-offs

- Boundary classes need small adapter code to pass primitive/plain Java values into pure logic.
- Not every Minecraft UI screen is controllable from Spigot alone.
- Some behavior still depends on ResourcePack and Fabric client-side support.

## Alternatives considered

### Keep JSON localization inside the ProtocolLib listener

Rejected because it makes packet I/O, player context, JSON parsing, and localization rules harder to test separately.

### Treat the whole plugin as one i18n layer

Rejected because TreasureRun uses different mechanisms for plugin messages, Minecraft standard UI keys, ResourcePack assets, and Fabric runtime synchronization.

## Verification evidence

The boundary is verified by tests that check:

- the ProtocolLib listener delegates JSON localization to `PacketI18nJsonLocalizer`
- JSON parsing/serialization does not live in the listener
- `plugin.i18n` does not import Bukkit, ProtocolLib, Fabric, or Minecraft runtime APIs

This supports the claim that TreasureRun separates the Minecraft-dependent boundary from pure Java i18n logic.
