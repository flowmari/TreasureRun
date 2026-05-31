# Architecture and Trade-off Summary

TreasureRun is an open-source Java / Spigot Minecraft plugin that combines gameplay, persistence, CI verification, and a platform-boundary i18n architecture.

## The problem

Minecraft text is not controlled from one place.

A Spigot plugin can control its own plugin messages, but Minecraft standard UI text is split across several boundaries:

- server-side plugin messages;
- server-to-client packets;
- client-side language assets;
- ResourcePack-loaded translation keys;
- optional client-side runtime state.

A Spigot plugin alone cannot honestly claim full control over every Minecraft UI surface.

## The design decision

TreasureRun separates the problem into layers instead of hiding the boundary:

| Layer | Responsibility |
|---|---|
| Spigot plugin YAML | Plugin-owned gameplay and command messages |
| ProtocolLib boundary | Observes and rewrites reachable translatable packet content |
| Pure Java packet localizer | Tests JSON localization logic without Bukkit, ProtocolLib, Fabric, or Minecraft runtime imports |
| ResourcePack language assets | Provides Minecraft standard translation-key assets |
| Fabric runtime sync | Optionally applies client-side language switching and resource reload |
| Docker runtime | Verifies the plugin in a reproducible local server environment |
| MySQL / ranking repositories | Persists gameplay and ranking data |
| Ranking API | Provides an optional read-only HTTP evidence slice over the same ranking schema |

## Why this matters

The project demonstrates an engineering habit that is valuable beyond Minecraft:

- identify a real platform boundary;
- avoid pretending the boundary does not exist;
- separate platform-dependent adapters from pure logic;
- verify behavior with tests, CI, Docker, checksums, and runtime evidence;
- document explicit non-claims.

## What is intentionally not claimed

TreasureRun does not claim:

- native-level translation quality for every experimental locale;
- full control over every Minecraft client screen;
- Paper compatibility until separately tested;
- production-scale server operations;
- payment or financial-domain backend experience.

## Concise recruiter-facing explanation

I built TreasureRun, an open-source Java / Spigot Minecraft plugin that demonstrates platform-boundary i18n. Minecraft standard UI text is split between server plugin messages, packet content, ResourcePack language assets, and client-side language state, so a Spigot plugin alone cannot control every surface. I separated the system into Spigot, ProtocolLib, ResourcePack, optional Fabric runtime sync, and a pure Java packet-localization core, then protected the design with CI, Docker runtime checks, ResourcePack checksum verification, and MySQL/Testcontainers evidence for the ranking boundary.
