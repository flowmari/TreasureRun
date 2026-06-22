# ResourcePack Artifact Integrity Test

## Purpose

TreasureRun treats Minecraft i18n as a platform-boundary problem rather than a README-only feature claim. The Resource Pack layer is checked through two explicit contracts: the retained shared artifact contract and the configured per-language fallback routing contract.

## What the Test Verifies

[`ResourcePackArtifactIntegrityTest`](../../../src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java) verifies two related but distinct responsibilities.

### Retained shared ResourcePack artifact

The retained shared multilingual ZIP remains locally inspectable and is checked for:

- ZIP / `.sha1` consistency;
- agreement with the top-level `resourcePack.sha1` configuration field;
- `pack.mcmeta` presence;
- 24 language JSON entries;
- 8039 Minecraft language keys per JSON entry;
- representative Minecraft standard UI keys.

### Configured versioned fallback routes

The test also checks:

- the committed published-manifest snapshot contains 23 language-specific asset entries;
- `config.yml` contains the same 23 fallback language routes;
- each configured URL points to the matching versioned GitHub prerelease asset filename;
- each configured SHA-1 equals the reviewed SHA-1 stored in the manifest snapshot;
- the 23 routed languages use distinct URLs and reviewed SHA-1 values.

The manifest fixture is stored at:

```text
src/test/resources/i18n/release-assets/v0.1.2-alpha-resourcepack-fallback.sha1
```

## Why This Matters

Before per-language routing, every fallback entry pointed to one shared ZIP and could be checked against one shared SHA-1 value. After the routing change, that assertion would be wrong: each fallback language has its own published asset and checksum.

The updated test therefore protects both contracts:

```text
retained shared artifact changes without its checksum/config update
configured per-language route changes without matching the reviewed manifest snapshot
```

The separate reproducible-build verification continues to establish that fresh per-language ZIPs are deterministic and match the canonical ResourcePack and Fabric source payloads.

## Evidence Boundary

This test establishes artifact and configuration integrity. It does not claim that a player has already observed every routed language on screen in a vanilla Minecraft client.

Display-level vanilla-client evidence is recorded separately after representative `/lang` runtime tests.

## Related Files

- [`ResourcePackArtifactIntegrityTest`](../../../src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java)
- [`scripts/check_fallback_resourcepack_generation.py`](../../../scripts/check_fallback_resourcepack_generation.py)
- [`docs/I18N_RESOURCEPACK_ALIASING_FALLBACK.md`](../../I18N_RESOURCEPACK_ALIASING_FALLBACK.md)
- [`PacketI18nJsonLocalizer`](../../../treasurerun-i18n-core/src/main/java/com/treasurerun/i18n/PacketI18nJsonLocalizer.java)
- [`ADR-001: Packet i18n Ports and Adapters`](../../adr/ADR-001-packet-i18n-ports-and-adapters.md)
