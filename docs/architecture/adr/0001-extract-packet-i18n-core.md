# ADR 0001: Extract packet i18n logic into an internal core module

Status: Accepted

## Context

External first-impression feedback noted that TreasureRun could look like two things shipped together:

- a gameplay plugin
- an i18n adapter or library

That feedback was useful because the README and code structure needed to make the boundary clearer.

TreasureRun remains a gameplay plugin first. The i18n work exists to support the gameplay plugin and to document the boundary between plugin-controlled text, server-to-client packet text, ResourcePack language assets, and client-only UI.

After the README was clarified, the next step was to make the boundary visible in code.

## Decision

Extract the pure Java packet JSON localisation logic into an internal Gradle module:

```text
treasurerun-i18n-core
  └─ pure Java packet JSON localisation logic

main TreasureRun plugin
  └─ Bukkit / ProtocolLib adapter
  └─ gameplay integration
```

The first extracted class is:

- `PacketI18nJsonLocalizer`

Its pure Java tests were moved with it.

The Bukkit / ProtocolLib listener remains in the main gameplay plugin.

## Why this boundary

`PacketI18nJsonLocalizer` does not need Bukkit, Spigot, ProtocolLib, Fabric, or Minecraft runtime APIs.

That makes it a safe first extraction candidate.

The ProtocolLib listener is different. It is an adapter. It belongs in the gameplay plugin because it connects the pure localisation logic to Minecraft packet handling.

## Why not create a separate public plugin or library now

A separate public plugin or library would require a larger design decision:

- public API boundaries
- versioning
- dependency management
- distribution
- setup instructions
- compatibility expectations
- release workflow
- additional documentation

Doing that immediately would make the project harder to review and test.

The safer first step is an internal module boundary.

## Consequences

Positive:

- the pure i18n logic is no longer only mixed into the gameplay plugin source tree
- the gameplay plugin remains the main entry point
- Minecraft-specific integration stays in adapter code
- tests can verify that the core module remains platform-free
- future extraction work has a clearer starting point

Trade-offs:

- the project now has a small Gradle multi-module structure
- future contributors need to understand the internal module boundary
- this is not yet a public reusable library

## Follow-up

Future work may evaluate whether more platform-independent localisation logic should move into `treasurerun-i18n-core`.

A separate public plugin or library should only be considered after the internal module boundary proves stable.
