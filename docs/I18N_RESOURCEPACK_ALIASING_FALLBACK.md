# Vanilla Client Resource Pack Fallback: Current Runtime Contract

## Purpose

TreasureRun uses several i18n layers because Minecraft text is split across plugin-owned messages, server-observable packet content, resource-pack language assets, and client-side language state.

This document describes the configured vanilla-client Resource Pack fallback route after the per-language GitHub Release asset routing change.

## Configured Fallback Routing

TreasureRun supports two relevant client paths:

- **Client with the optional Fabric companion mod**: the server can send the selected TreasureRun language code to the companion mod, which can apply the mapped Minecraft locale and reload client resources.
- **Vanilla client without the optional Fabric companion mod**: the fallback service reads the player's stored TreasureRun language selection and sends the Resource Pack entry configured for that language.

The fallback configuration now maps each of the 23 supported TreasureRun language codes to its own published, versioned GitHub prerelease asset. Each configured route includes the SHA-1 value recorded in the published asset manifest.

Representative routing entries are:

```text
/lang de  -> treasurerun-i18n-pack-de.zip
/lang ja  -> treasurerun-i18n-pack-ja.zip
/lang got -> treasurerun-i18n-pack-got.zip
```

The routed configuration is checked by `ResourcePackArtifactIntegrityTest`. Reproducible artifact generation is checked separately by `scripts/check_fallback_resourcepack_generation.py`.

## Important Boundary

A server-delivered Resource Pack does not, by itself, change a vanilla client's active language setting.

The configured routing establishes that TreasureRun can select a language-specific fallback pack for each stored language choice. It does **not** by itself establish that every selected-language display path has been observed in a vanilla Minecraft client.

The accurate claim at this stage is:

> TreasureRun configures language-specific Resource Pack fallback routes for vanilla clients using published GitHub prerelease assets with SHA-1-verified routing metadata. Representative in-game behavior on vanilla clients has not yet been verified; it will be covered in dedicated runtime testing.

## Layer Responsibilities

| Text or behavior surface | Mechanism |
| --- | --- |
| TreasureRun-owned gameplay messages | YAML-backed plugin i18n |
| Server-observable translatable packet content | ProtocolLib boundary adapter and pure Java packet localizer |
| Minecraft translation-key assets sent to vanilla fallback clients | Versioned language-specific Resource Pack asset selected through `config.yml` |
| Client-side selected-language application and resource reload | Optional Fabric companion mod |
| Fully client-local or pre-login language surfaces | Outside the control of a Spigot plugin alone |

## Artifact Contract

The routed Resource Pack assets were published under the prerelease tag:

```text
v0.1.2-alpha-resourcepack-fallback
```

The repository stores a small SHA-1 manifest fixture for automated route verification:

```text
src/test/resources/i18n/release-assets/v0.1.2-alpha-resourcepack-fallback.sha1
```

This fixture records the published asset filenames and reviewed SHA-1 values. This routing change does not add new ZIP binaries to the repository.

A retained shared multilingual ZIP remains in the repository for its existing local artifact-coverage contract. Historical binary cleanup and Git-history decisions are separate concerns.

## Evidence Pointers

The configured routing contract can be reviewed in:

- `src/main/resources/config.yml`
- `src/main/java/plugin/ResourcePackFallbackService.java`
- `src/main/java/plugin/ResourcePackFallbackJoinListener.java`
- `src/main/java/plugin/LangCommand.java`
- `src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java`
- `src/test/resources/i18n/release-assets/v0.1.2-alpha-resourcepack-fallback.sha1`
- `scripts/check_fallback_resourcepack_generation.py`

Historical notes describing the earlier shared-ZIP runtime remain preserved as time-specific evidence rather than being rewritten retroactively.
