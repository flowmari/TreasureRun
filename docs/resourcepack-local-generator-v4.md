# ResourcePack V4: local-only distribution boundary

This document defines the repository and distribution contract for ResourcePack V4.

## Preserved runtime boundaries

- 23 plugin-side `languages/*.yml` sources
- language selection GUI and per-player persistence
- ProtocolLib packet localisation
- the pure-Java i18n core
- Fabric Java runtime code
- database, ranking, and license packaging

## Distribution boundary

Client-pack generation is local-only. Generated packs are built from the operator's own installed Minecraft 1.20.1 assets, written outside the repository, and must not be published.

- No Minecraft language payload JSON is stored in the repository.
- No reconstructed full ResourcePack ZIP is stored or published.
- Both automatic server delivery paths are disabled by default.
- Seventeen reviewed official locale mappings may be generated from the operator's local Minecraft 1.20.1 assets.
- Six custom/missing mappings (`ang`, `asl_gloss`, `lzh`, `non`, `ojp`, `sa`) remain server/plugin-side only for this preview.
- The `lzh -> lzh` alias candidate is not adopted silently.
- Generated packs are written outside the repository, contain `treasurerun-local-only.json`, and are listed in `local-pack-manifest.tsv`.
- Existing GitHub Release assets are not deleted or replaced by this change. They remain untouched until a replacement delivery contract and a later release have passed their own verification.

## Verification

CI verifies the repository boundary without downloading or redistributing Minecraft assets. Local integration checks verify deterministic generation against the operator's own installed Minecraft assets.
