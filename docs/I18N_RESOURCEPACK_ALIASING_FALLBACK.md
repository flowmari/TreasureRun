# Vanilla Client ResourcePack Fallback: V4 Runtime and Distribution Contract

> **V4 local-only ResourcePack contract:** TreasureRun does not store or publish Minecraft language payload JSON files or reconstructed full ResourcePack ZIP files. Client packs for the 17 reviewed official locale mappings are generated only from the operator's own installed Minecraft 1.20.1 assets. The six custom/missing mappings remain available to plugin-side i18n, while their client-pack generation remains held.

## Runtime boundary

TreasureRun preserves the 23 plugin-side language sources, the language-selection GUI, per-player language storage, ProtocolLib packet localisation, the pure-Java i18n core, and Fabric Java runtime language synchronisation.

Automatic public ResourcePack delivery is disabled by default. TreasureRun does not select or publish per-language GitHub Release packs as the active V4 delivery contract.

## Local generation

Eligible client packs are generated locally from the server operator's own installed Minecraft 1.20.1 assets by `tools/client-resourcepack/build-local-official-language-pack.py`. Generated packs are written outside the repository, contain `treasurerun-local-only.json`, and are listed in `local-pack-manifest.tsv`.

Generated client packs are local-only and must not be uploaded as GitHub Release or Modrinth artifacts.

## Held mappings

The following custom or missing client-pack mappings remain deliberately held:

- `ang -> ang_gb`
- `asl_gloss -> asl_us`
- `lzh -> lzh_hant`
- `non -> non_is`
- `ojp -> ojp_jp`
- `sa -> sa_in`

TreasureRun does not silently fall back to English for these mappings, and it does not silently adopt `lzh -> lzh`.

## Existing release assets

This contract does not delete or replace existing GitHub Release assets. Existing assets remain untouched until a replacement delivery contract and a later release have passed their own verification.

Historical routing and release records remain preserved as time-specific evidence; they are not the active V4 distribution contract.
