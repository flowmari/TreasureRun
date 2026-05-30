> Historical note: this document records the earlier reproducible-build step before
> language-specific fallback packs were moved to versioned GitHub Release assets.
> Current `main` now routes the 23 configured fallback packs through Release URLs,
> while retaining the shared multilingual ZIP for current config/test coverage.

# Reproducible Per-Language Fallback Resource Pack Builds

## Purpose

This change introduces a reproducible build contract for language-specific fallback resource packs intended for a later vanilla-client delivery path.

The runtime currently configured on `main` still routes non-Fabric fallback entries to one shared multilingual ZIP. This change does not alter that routing, publish release assets, or claim that per-language vanilla-client delivery is active.

## Why This Contract Is Needed

A pre-implementation audit found two reasons not to publish the existing language-specific ZIP files directly:

1. the existing Gothic ZIP no longer matched the current canonical `got_de.json` payload on 14 translation keys;
2. two clean generations from the same checked-in source payloads produced different ZIP SHA-1 values because ZIP metadata was not normalized.

Public delivery through immutable release assets should be based on fresh, reproducible artifacts rather than stale output or build-time ZIP variance.

## Build Contract

The generator now builds fallback resource packs for **Minecraft Java Edition 1.20.1** with `pack_format: 15`.

It also:

- writes to `build/generated/fallback-resourcepacks` by default rather than overwriting tracked binary artifacts;
- normalizes ZIP entry timestamps and file metadata;
- consumes the server-delivered resource pack language layer as the artifact source;
- supports an explicit output directory for isolated verification.

The verification script performs two clean temporary builds and verifies that:

- one ZIP is produced for each configured TreasureRun language;
- every generated ZIP includes valid Minecraft 1.20.1 resource pack metadata;
- every ZIP contains the expected locale aliases;
- every alias payload contains exactly 8039 Minecraft 1.20.1 translation keys;
- every alias payload matches its ResourcePack source JSON;
- the corresponding ResourcePack and Fabric JSON payloads remain semantically aligned;
- both clean builds are byte-identical and yield identical SHA-1 values in the pinned CI toolchain.

## Scope Boundary

This pull request intentionally does **not**:

- create a GitHub Release;
- upload language-specific ZIP assets;
- change `src/main/resources/config.yml`;
- alter Java fallback dispatch behavior;
- claim that `/lang <language>` currently sends a language-specific ZIP to vanilla clients.

A later implementation pull request can publish fresh verified artifacts as immutable GitHub Release assets, route each fallback language entry to its matching URL and SHA-1 value, and capture vanilla-client runtime evidence before presenting per-language delivery as active behavior.

## Engineering Significance

This establishes a reproducible build contract before any public delivery path is enabled. It keeps the runtime claim narrower than the evidence, prevents stale Gothic output from being published, and makes future immutable artifact routing reviewable in CI.
