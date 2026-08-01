# TreasureRun client ResourcePack source boundary

TreasureRun does not store Minecraft language payload JSON files or a reconstructed full ResourcePack ZIP in this directory.

Eligible client packs are generated locally with `tools/client-resourcepack/build-local-official-language-pack.py` from the server operator's own installed Minecraft 1.20.1 assets. Each generated pack contains `pack.mcmeta` and `treasurerun-local-only.json`; the generator also writes `local-pack-manifest.tsv`.

Generated client packs are local-only and must not be uploaded as GitHub Release or Modrinth artifacts. Automatic public ResourcePack delivery is disabled by default.

The 17 reviewed official locale mappings are eligible for local generation. The following six custom or missing mappings remain deliberately held:

- `ang -> ang_gb`
- `asl_gloss -> asl_us`
- `lzh -> lzh_hant`
- `non -> non_is`
- `ojp -> ojp_jp`
- `sa -> sa_in`

TreasureRun does not silently fall back to English for held mappings and does not silently adopt `lzh -> lzh`.

This change does not delete or replace existing GitHub Release assets. Historical release and runtime evidence remain preserved as time-specific records rather than the active V4 distribution contract.
