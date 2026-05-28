# Vanilla Client Resource Pack Fallback: Versioned Release-Asset Routing Verification Note

## Status

This note describes the configured fallback routing contract introduced after the shared multilingual ZIP baseline was verified and preserved in earlier records.

Earlier shared-ZIP evidence remains available for traceability at:

- `docs/verification/i18n/shared-resourcepack-runtime-alignment-20260527.md`
- `docs/archive/NON_MOD_RESOURCEPACK_FALLBACK_HISTORICAL_BEFORE_ALIGNMENT_20260527.md`

## Configured Routing Contract

For vanilla clients without the optional Fabric companion mod, `resourcePackFallback.packs.*` now maps each supported TreasureRun language to a corresponding Resource Pack asset published under:

```text
v0.1.2-alpha-resourcepack-fallback
```

Representative configured routes are:

```text
/lang de  -> treasurerun-i18n-pack-de.zip
/lang ja  -> treasurerun-i18n-pack-ja.zip
/lang got -> treasurerun-i18n-pack-got.zip
```

Each configured URL is paired with the SHA-1 value recorded in the published asset manifest snapshot committed for automated verification.

## What This Routing Change Verifies

This routing contract establishes that:

- 23 language-specific Resource Pack ZIP assets were published through the dedicated GitHub prerelease;
- `config.yml` maps each supported fallback language to its corresponding published asset URL and SHA-1 value;
- `ResourcePackArtifactIntegrityTest` checks the configured routes against the committed manifest snapshot;
- the retained shared ResourcePack ZIP remains checked against its local checksum and top-level configuration entry;
- the reproducible-build contract continues to verify metadata, alias payloads, 8039-key coverage, ResourcePack/Fabric semantic alignment, and deterministic ZIP output.

## What Still Requires Runtime Evidence

A valid Resource Pack route and checksum contract are not the same as an observed Minecraft screen result.

After this routing change is merged, representative vanilla-client tests should capture evidence for selections such as:

```text
/lang de
/lang ja
/lang got
```

Until that evidence is recorded, the project should claim configured and verified language-specific asset routing, not completed display-level validation for every vanilla-client surface.

## Accurate Current Claim

> TreasureRun configures language-specific Resource Pack fallback routes for vanilla clients using published GitHub prerelease assets with SHA-1-verified routing metadata. Representative in-game behavior on vanilla clients has not yet been verified; it will be covered in dedicated runtime testing.

## Verification Basis

- `src/main/resources/config.yml`
- `src/test/java/plugin/i18n/ResourcePackArtifactIntegrityTest.java`
- `src/test/resources/i18n/release-assets/v0.1.2-alpha-resourcepack-fallback.sha1`
- `scripts/check_fallback_resourcepack_generation.py`
- GitHub prerelease `v0.1.2-alpha-resourcepack-fallback`
