# Vanilla Client Resource Pack Fallback: Current Runtime Contract

## Purpose

TreasureRun uses several i18n layers because Minecraft text is not owned by a single runtime boundary. Plugin messages, server-observable translatable components, resource-pack language assets, and the client's active language setting each require different mechanisms.

This document describes the Resource Pack behavior configured on `main`, as verified during this documentation correction.

## Currently Verified Behavior

TreasureRun supports two relevant client paths:

- **Client with the optional Fabric companion mod**: the server can send the selected TreasureRun language code to the companion mod, which can apply the mapped Minecraft locale and reload client resources.
- **Vanilla client without the optional Fabric companion mod**: the fallback service can send the configured Resource Pack entry associated with the player's stored language selection.

At present, every configured `resourcePackFallback.packs.*` entry resolves to the same shared multilingual artifact:

```text
resourcepacks/generated/treasurerun-i18n-pack.zip
```

The shared pack contains Minecraft language JSON assets for the supported locale mappings. Once the client accepts and applies the pack, it can provide translated values for translation keys resolved through the client's already active locale.

## Important Boundary

A server-delivered resource pack does not, by itself, change a vanilla client's active language setting.

Accordingly, the runtime currently configured on `main` does **not** claim that selecting `/lang de`, `/lang ja`, or any other TreasureRun language on a vanilla client causes the server to deliver a distinct language-specific alias ZIP or forces Minecraft to switch to that selected locale.

The accurate current claim is:

> TreasureRun currently provides a shared multilingual Resource Pack layer for Minecraft translation-key assets. Applying a selected client language and reloading resources at runtime requires the optional Fabric companion mod.

## Layer Responsibilities

| Text or behavior surface | Current mechanism |
| --- | --- |
| TreasureRun-owned gameplay messages | YAML-backed plugin i18n |
| Server-observable translatable packet content | ProtocolLib boundary adapter and pure Java packet localizer |
| Minecraft translation-key assets exposed through a server resource pack | Shared multilingual Resource Pack ZIP |
| Client-side selected-language application and resource reload | Optional Fabric companion mod |
| Fully client-local or pre-login language surfaces | Outside the control of a Spigot plugin alone |

## Retained Language-Specific ZIP Artifacts

The repository still contains generated language-specific ZIP artifacts, including files such as:

```text
resourcepacks/generated/treasurerun-i18n-pack-de.zip
resourcepacks/generated/treasurerun-i18n-pack-ja.zip
resourcepacks/generated/treasurerun-i18n-pack-ojp.zip
```

Those files remain in the repository as retained generated artifacts for traceability. Their presence does not mean that the configuration currently committed on `main` dispatches a different ZIP for each selected vanilla-client language.

## Deferred Implementation: Release-Hosted Language-Specific Delivery

A separate implementation pull request is planned to add language-specific Resource Pack delivery for vanilla clients. That work would require:

1. defining the routed language-specific artifact contract;
2. publishing language-specific ZIP files as GitHub Release assets;
3. configuring individual URLs and SHA-1 values for the routed artifacts;
4. extending tests and CI to verify the routed release-hosted artifacts;
5. recording in-game vanilla-client evidence for representative language selections.

That work is not part of this documentation correction.

Separately, moving future binary distribution to Release assets would not remove ZIP data already present in Git history. Any history-reduction decision must be evaluated independently because it may affect existing clones, forks, references, and contributor workflows.

## Evidence Pointers

Current runtime evidence should be read from:

- `src/main/resources/config.yml`
- `src/main/java/plugin/ResourcePackFallbackService.java`
- `src/main/java/plugin/ResourcePackFallbackJoinListener.java`
- `src/main/java/plugin/LangCommand.java`
- `src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java`
- `docs/verification/i18n/shared-resourcepack-runtime-alignment-20260527.md`

The earlier verification record has been preserved verbatim at:

- `docs/archive/NON_MOD_RESOURCEPACK_FALLBACK_HISTORICAL_BEFORE_ALIGNMENT_20260527.md`
