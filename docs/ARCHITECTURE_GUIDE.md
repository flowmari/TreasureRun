# Architecture Review Guide — TreasureRun

TreasureRun is an open-source Minecraft Spigot plugin project built as a Java engineering case study.

The project is not only a mini-game. It demonstrates how to design around a real platform boundary: Minecraft text is split between server-side plugin messages, server-to-client packets, ResourcePack language assets, and client-side language state. A Spigot plugin alone cannot fully control every piece of Minecraft UI text, so TreasureRun uses a layered i18n architecture instead of pretending the boundary does not exist.

## 30-second summary

TreasureRun demonstrates:

- Java / Spigot plugin development
- Platform-boundary i18n architecture
- ProtocolLib packet-boundary handling
- ResourcePack language asset generation
- Fabric runtime language sync
- Testable pure Java i18n logic
- Docker-based server verification
- MySQL-backed ranking persistence
- GitHub Actions quality gates
- Documentation-first OSS presentation

## Core engineering problem

Minecraft has multiple text ownership layers:

1. Custom plugin messages controlled by the Spigot plugin
2. Server-to-client translatable packet components
3. Standard Minecraft UI strings resolved from client language files
4. Client-only screens that a server plugin cannot fully control

TreasureRun handles this by separating responsibilities instead of forcing everything into one layer.

## Architecture at a glance

| Layer | Role |
| --- | --- |
| Spigot plugin YAML | Plugin-side user-facing messages |
| ProtocolLib boundary | Server-to-client packet observation / localization boundary |
| Pure Java packet localizer | Testable i18n transformation logic without Bukkit / ProtocolLib imports |
| ResourcePack language assets | Minecraft standard translation-key assets |
| Fabric runtime sync | Client-side language switching by selected language code |
| Docker Spigot server | Runtime verification environment |
| GitHub Actions | CI, i18n checks, ResourcePack integrity checks |

## i18n scope

TreasureRun currently supports 23 internal plugin language mappings, including experimental historical / constructed presentation layers such as Old Japanese, Literary Chinese, Sanskrit, ASL gloss, Old English, Old Norse, and Gothic.

The ResourcePack layer includes generated Minecraft language JSON assets with 8039 translation keys per language asset, aligned for Minecraft 1.20.1 compatibility.

The Gothic locale is mapped as:

```text
TreasureRun language code: got
Minecraft locale code:    got_de
```

This is intentionally documented as experimental. The value is not a claim of native-level Gothic fluency. The value is the repeatable i18n pipeline: adding a language through YAML, lang-map, ResourcePack JSON, Fabric assets, ZIP/SHA1 integrity checks, tests, and CI.

## Evidence

Important evidence files:

- `docs/verification/i18n/gothic-locale-expansion.md`
- `docs/verification/i18n/gothic-runtime-evidence.md`
- `docs/verification/i18n/ang-non-outcome-proverbs.md`
- `src/test/java/plugin/i18n/LanguageCodeMappingIntegrityTest.java`
- `src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java`
- `scripts/check_minecraft_packet_coverage.py`

Important project assets:

- `src/main/resources/lang-map.yml`
- `fabric-i18n-mod/src/main/resources/lang-map.yml`
- `src/main/resources/languages/got.yml`
- `resourcepacks/treasurerun-i18n-pack/assets/minecraft/lang/got_de.json`
- `fabric-i18n-mod/src/main/resources/assets/minecraft/lang/got_de.json`
- `resourcepacks/generated/treasurerun-i18n-pack.zip`
- `resourcepacks/generated/treasurerun-i18n-pack.zip.sha1`

## What this project proves

TreasureRun is useful as a technical review artifact because it shows the ability to:

- identify a platform limitation honestly
- design a multi-layer workaround without overclaiming
- separate platform-dependent code from testable core logic
- create regression tests for architecture boundaries
- maintain generated assets with integrity checks
- document what works and what remains outside the server's control
- operate a project in an OSS-like style with CI and verification evidence

## Honest limitations

TreasureRun does not claim that a Spigot plugin can control all Minecraft client text. Some UI paths are client-owned, pre-login, settings-screen, or otherwise outside the full control of a server plugin.

The project is best understood as a platform-boundary i18n system: it pushes the server-side and ResourcePack-accessible layers as far as practical, while documenting the boundary clearly.

## Suggested reading path

For a quick review:

1. Read the README overview.
2. Open this file.
3. Check the i18n evidence docs.
4. Check the i18n tests.
5. Check the latest GitHub Actions results.
6. Inspect the `got -> got_de` mapping and ResourcePack JSON assets.

## Resume wording

A concise resume bullet:

> Built TreasureRun, an open-source Java / Spigot Minecraft plugin demonstrating platform-boundary i18n architecture across Spigot YAML messages, ProtocolLib packet boundaries, ResourcePack language assets, Fabric runtime language sync, Docker verification, MySQL persistence, and GitHub Actions quality gates.

A stronger technical bullet:

> Designed a layered i18n system for Minecraft 1.20.1 that separates plugin-controlled messages, packet-boundary localization, ResourcePack translation assets, and client-side runtime language sync, with regression tests protecting language mapping, ResourcePack key coverage, and SHA1 integrity.

