# Project Overview: TreasureRun

TreasureRun is a Java-based Minecraft Spigot 1.20.1 plugin that demonstrates backend/plugin engineering, i18n architecture, and platform-boundary problem solving under real Minecraft client/server constraints.

This project is not just a Minecraft mini-game. It is an engineering case study in working around a real platform limitation: a Spigot plugin alone cannot fully control all Minecraft standard UI text, because part of the text pipeline lives on the Minecraft client side.

To address that limitation, TreasureRun separates the problem into multiple layers:

- a **ProtocolLib boundary adapter** for server-to-client packet access
- a **ResourcePack language layer** for Minecraft standard translation-key assets
- **Fabric runtime language sync support** for client-side language switching
- a **pure Java packet JSON localizer** for testable packet-localization logic

The result is a testable multi-layer i18n system that separates Minecraft-dependent runtime code from platform-free localization logic.

---

## What this project demonstrates

TreasureRun demonstrates the following engineering skills:

- Java 17 plugin development for Spigot 1.20.1
- Bukkit / Spigot plugin architecture
- ProtocolLib-based packet boundary handling
- JSON component localization for Minecraft packet messages
- ResourcePack-based Minecraft standard UI localization
- Fabric-side runtime language synchronization support
- separation of platform-dependent adapters from pure Java logic
- CI-backed i18n verification
- ResourcePack ZIP / SHA-1 consistency checks
- test-driven architectural boundary protection
- OSS-ready project structure with contribution and security documents

---

## Core engineering challenge

Minecraft plugin messages are relatively easy to localize because they are owned by the plugin.

Minecraft standard UI text is different.

Some standard UI messages are resolved through the Minecraft client, Minecraft language files, translatable JSON components, ResourcePacks, or client-side language state. A Spigot plugin alone cannot reliably control every part of that pipeline.

TreasureRun treats this as a platform-boundary problem rather than a simple translation problem.

The project separates the system into distinct responsibilities:

| Layer | Responsibility |
| --- | --- |
| Spigot plugin layer | Game logic, commands, player state, server-side behavior |
| ProtocolLib boundary layer | Intercepts and rewrites selected server-to-client packet JSON components |
| Pure Java packet localizer | Parses Minecraft JSON components and converts translation keys into localized text |
| ResourcePack layer | Provides Minecraft standard translation-key assets to the client |
| Fabric runtime sync support | Supports runtime client language switching without sending large language payloads |

This makes the architecture easier to test, audit, explain, and maintain.

---

## Key architectural claim

TreasureRun can be described as follows:

> TreasureRun separates a Minecraft standard UI boundary that cannot be fully controlled by Spigot alone into a ProtocolLib packet boundary, ResourcePack language assets, Fabric runtime sync support, and a pure Java packet localizer.  
> This turns a client/server platform limitation into a testable multi-layer i18n architecture.

This is the main engineering story behind the project.

---

## Boundary design

The most important design decision is the separation between the ProtocolLib adapter and the pure localization core.

### Minecraft-dependent boundary

`LocalizedPacketMessageProtocolListener` is the runtime boundary adapter.

It is responsible for:

- listening to outgoing packet events
- reading `WrappedChatComponent` and packet string components
- resolving the target player's selected language
- adapting TreasureRun i18n placeholders
- writing localized JSON components back to packets

This class is allowed to depend on Bukkit, Spigot, ProtocolLib, Player objects, and plugin runtime APIs.

### Pure Java localization core

`PacketI18nJsonLocalizer` contains the platform-free packet-localization logic.

It is responsible for:

- parsing Minecraft JSON text components
- finding `translate` keys
- converting Minecraft translation keys into TreasureRun packet i18n keys
- extracting `with` arguments as placeholders
- returning a rewritten JSON text component when replacement is safe

This class does not depend on Bukkit, Spigot, ProtocolLib, Fabric, or Minecraft runtime APIs.

That separation matters because the core localization behavior can be tested without starting a Minecraft server.

---

## Testable architecture

TreasureRun protects the boundary with tests.

The most important tests to review are:

- `src/test/java/plugin/i18n/PacketI18nJsonLocalizerTest.java`
- `src/test/java/plugin/i18n/PureI18nPackageBoundaryTest.java`
- `src/test/java/plugin/LocalizedPacketMessageProtocolListenerTest.java`
- `src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java`
- `src/test/java/plugin/i18n/ResourcePackExactKeySetConsistencyTest.java`
- `src/test/java/plugin/i18n/LanguageCodeMappingIntegrityTest.java`
- `src/test/java/plugin/i18n/PacketI18nSafeFallbackBehaviorTest.java`

`PureI18nPackageBoundaryTest` acts as an architectural fitness function. It scans the pure i18n package and fails the build if platform APIs are imported into the pure localization layer.

The forbidden imports include:

- `org.bukkit.*`
- `com.comphenix.protocol.*`
- `net.fabricmc.*`
- `net.minecraft.*`

This means the boundary is not just documented. It is checked automatically.

The ResourcePack and locale-mapping tests also make the generated i18n assets reviewable. They verify ZIP/SHA-1 consistency, exact language JSON key-set consistency, internal language-code mapping to Minecraft locale files, and safe fallback behavior for packet JSON localization.

See `docs/verification/i18n/i18n-test-coverage.md` for the claim-to-test summary.

---

## Files worth reviewing first

For a quick technical review, start with these files:

| File | Why it matters |
| --- | --- |
| `README.md` | Main project overview and architecture summary |
| `docs/adr/ADR-001-packet-i18n-ports-and-adapters.md` | Design rationale for the PacketI18n boundary |
| `docs/verification/i18n/packet-i18n-boundary-refactor.md` | Verification notes for the boundary refactor |
| `src/main/java/plugin/LocalizedPacketMessageProtocolListener.java` | ProtocolLib-facing boundary adapter |
| `src/main/java/plugin/i18n/PacketI18nJsonLocalizer.java` | Pure Java packet-localization core |
| `src/test/java/plugin/i18n/PureI18nPackageBoundaryTest.java` | Test that protects the pure i18n package from platform imports |
| `src/test/java/plugin/i18n/PacketI18nJsonLocalizerTest.java` | Unit tests for packet JSON localization |
| `src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java` | Tests for generated ResourcePack ZIP, SHA-1, config, and 8039-key coverage |
| `src/test/java/plugin/i18n/ResourcePackExactKeySetConsistencyTest.java` | Test that every ResourcePack language JSON has the exact same key set |
| `src/test/java/plugin/i18n/LanguageCodeMappingIntegrityTest.java` | Test that TreasureRun internal language codes map to Minecraft locale files |
| `src/test/java/plugin/i18n/PacketI18nSafeFallbackBehaviorTest.java` | Test that packet i18n fails safely when replacement is unsafe |
| `docs/verification/i18n/i18n-test-coverage.md` | Claim-to-test summary for automated i18n verification |
| `.github/workflows/i18n-expansion-ci.yml` | CI checks for i18n expansion and language mapping |
| `.github/workflows/resourcepack-sha1.yml` | ResourcePack ZIP / SHA-1 verification workflow |

---

## Why this matters for engineering roles

TreasureRun shows more than basic feature implementation.

It demonstrates the ability to:

- identify a platform constraint
- separate runtime boundaries from pure logic
- design around client/server responsibility limits
- create testable seams around hard-to-test infrastructure
- use CI to prevent architectural drift
- document trade-offs and verification evidence
- turn a game-plugin feature into a maintainable engineering system

This is especially relevant to roles involving:

- Java backend engineering
- platform engineering
- developer tooling
- localization / i18n infrastructure
- game server tooling
- QA automation with Java
- technical support engineering for developer products

---

## What I focused on as the developer

The main focus of this project was not only building gameplay.

The engineering focus was:

- understanding where Spigot can and cannot control Minecraft text
- separating Minecraft runtime access from pure localization logic
- designing ResourcePack and Fabric support around Minecraft's client-side language model
- creating repeatable checks for i18n assets and key coverage
- documenting the architecture so that the project can be reviewed by other engineers

In short, TreasureRun is a portfolio project designed to show platform-boundary reasoning, Java implementation, automated verification, and maintainable i18n architecture.

---

## Current maturity

TreasureRun is currently best described as an alpha-stage engineering portfolio project.

It already includes:

- working Spigot plugin code
- multi-language resource files
- ResourcePack generation and SHA-1 verification
- PacketI18n boundary separation
- unit tests and architectural boundary tests
- GitHub Actions workflows
- OSS-ready documentation files

Areas for future improvement include:

- reducing large generated artifacts in the repository
- moving heavy generated ResourcePack ZIPs to GitHub Releases
- adding more demo GIFs or short videos
- continuing to split large gameplay classes into smaller services
- expanding contributor-facing documentation

---

## Short technical summary

TreasureRun is a Java Spigot plugin project that demonstrates how to solve a real Minecraft platform-boundary problem.

A Spigot plugin alone cannot fully control Minecraft standard UI text, so the project separates the system into a ProtocolLib packet boundary, ResourcePack language assets, Fabric runtime sync support, and a pure Java packet localizer.

The most important part is that the Minecraft-dependent boundary and the pure localization logic are separated and protected by tests.

This makes TreasureRun a strong portfolio case study for Java, i18n architecture, platform constraints, CI verification, and maintainable engineering design.
