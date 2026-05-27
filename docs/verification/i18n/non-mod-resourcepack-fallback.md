# Vanilla Client Resource Pack Fallback: Current Verification Note

## Status

This page supersedes an earlier verification note that described language-specific Resource Pack alias ZIP delivery as though it were the runtime currently configured on `main`.

The earlier note has been preserved verbatim for traceability at:

- `docs/archive/NON_MOD_RESOURCEPACK_FALLBACK_HISTORICAL_BEFORE_ALIGNMENT_20260527.md`

## Currently Verified Runtime State

The configuration currently committed on `main` contains language-keyed `resourcePackFallback.packs.*` entries, but all of those entries point to the same shared multilingual ZIP:

```text
resourcepacks/generated/treasurerun-i18n-pack.zip
```

The same shared SHA-1 value is configured for those fallback entries.

The shared ZIP contains multiple Minecraft language JSON assets. It supports resource-pack-resolved translation-key behavior after the client accepts and applies the pack.

## What the Current Runtime Does Not Establish

The current configuration does not establish that a vanilla client receives a distinct language-specific ZIP when the player selects a different TreasureRun language.

In particular, the runtime currently configured on `main` does not prove the following behavior:

```text
/lang de  -> treasurerun-i18n-pack-de.zip
/lang ja  -> treasurerun-i18n-pack-ja.zip
/lang ojp -> treasurerun-i18n-pack-ojp.zip
```

Language-specific ZIP artifacts remain in the repository, and fallback dispatch code remains in the Java implementation. However, those facts alone do not demonstrate active language-specific delivery while the configured URLs all resolve to the shared multilingual ZIP.

## Accurate Current Claim

> For vanilla clients without the optional Fabric companion mod, TreasureRun currently provides a shared multilingual Resource Pack path for Minecraft translation-key assets. Runtime application of the selected client language and resource reload behavior require the optional Fabric companion mod.

## Verification Basis

The documentation alignment is based on the following current facts:

- `src/main/resources/config.yml` routes all fallback language entries to the shared multilingual ZIP;
- `ResourcePackFallbackService` still reads configured fallback entries;
- generated language-specific ZIP files remain present as retained artifacts;
- current Resource Pack integrity verification protects the shared ZIP and its configured SHA-1 path.

## Separate Future Work

Implementing language-specific delivery for vanilla clients would require a separate implementation pull request. That work should consider ZIP assets hosted on GitHub Releases, individual routed SHA-1 values, CI verification of those routed artifacts, and in-game vanilla-client evidence.

This documentation correction does not implement that delivery model and does not rewrite repository history.
