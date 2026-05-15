# ResourcePack Artifact Integrity Test

## Purpose

TreasureRun documents a multi-layer Minecraft i18n architecture, but the important point is that these claims are not left as README-only statements.

The generated ResourcePack is verified by a JUnit test so that the documented architecture, generated artifact, and runtime configuration stay aligned over time.

This makes the i18n work reviewable as an engineering system:

```text
Minecraft platform constraint
→ layered i18n architecture
→ generated ResourcePack artifact
→ JUnit artifact-integrity test
→ CI quality gate
```

## What this test verifies

[`ResourcePackArtifactIntegrityTest`](../../../src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java) verifies the generated ResourcePack artifact itself.

It checks:

- the generated ResourcePack ZIP exists
- the `.sha1` file exists
- the SHA-1 value of the ZIP matches the `.sha1` file
- all ResourcePack SHA values in `src/main/resources/config.yml` match the generated ZIP SHA
- the ResourcePack contains 21 Minecraft language JSON files
- every language JSON file contains 8039 Minecraft language keys
- important Minecraft standard UI keys exist in every language JSON file

## Why this matters

Minecraft standard UI text is split across server-side and client-side responsibility.

A Spigot plugin alone cannot fully control every Minecraft standard UI string, so TreasureRun uses a layered approach:

- ProtocolLib packet boundary
- server-side ResourcePack
- Fabric runtime language sync
- pure Java packet localizer

The ResourcePack is one of the generated artifacts that makes this workaround practical.  
Because the ResourcePack is generated and referenced by configuration, it is easy for the ZIP, `.sha1`, and `config.yml` values to drift apart.

This test prevents that drift from silently entering the repository.

## Claim-to-test traceability

| README / architecture claim | Verification |
| --- | --- |
| ResourcePack ZIP and SHA values are consistent | ZIP SHA is recalculated and compared with `.sha1` |
| `config.yml` points to the correct ResourcePack SHA | all `sha1:` values in `config.yml` are compared with the generated ZIP SHA |
| the ResourcePack contains 21 language JSON files | ZIP entries under `assets/minecraft/lang/*.json` are counted |
| each language JSON has 8039 Minecraft keys | each JSON object is parsed and its key count is checked |
| important Minecraft standard UI keys are covered | representative keys such as `menu.singleplayer`, `menu.multiplayer`, `menu.options`, `menu.quit`, `gui.cancel`, `multiplayer.title`, `connect.connecting`, and `connect.encrypting` are checked |

## Engineering value

This turns the ResourcePack layer from a manually inspected asset into a CI-verifiable contract.

In practical terms:

```text
If the ResourcePack ZIP changes but the SHA is not updated, tests fail.
If a language JSON file disappears, tests fail.
If a language JSON loses key coverage, tests fail.
If important Minecraft UI keys disappear, tests fail.
```

That means the project does not merely describe a workaround for Minecraft's server/client language boundary.

It demonstrates a stronger engineering loop:

1. identify the platform constraint
2. split the responsibility into testable layers
3. generate the artifact
4. verify the artifact directly
5. protect the result with CI

## Related files

- [`ResourcePackArtifactIntegrityTest`](../../../src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java)
- [`PacketI18nJsonLocalizer`](../../../src/main/java/plugin/i18n/PacketI18nJsonLocalizer.java)
- [`LocalizedPacketMessageProtocolListener`](../../../src/main/java/plugin/LocalizedPacketMessageProtocolListener.java)
- [`PureI18nPackageBoundaryTest`](../../../src/test/java/plugin/i18n/PureI18nPackageBoundaryTest.java)
- [`LocalizedPacketMessageProtocolListenerTest`](../../../src/test/java/plugin/LocalizedPacketMessageProtocolListenerTest.java)
- [`ADR-001: Packet i18n Ports and Adapters`](../../adr/ADR-001-packet-i18n-ports-and-adapters.md)
