# Shared Resource Pack Runtime Alignment — 2026-05-27

## Reason for This Record

While preparing TreasureRun's contributor-first public-facing experience, a documentation/runtime mismatch was identified in the vanilla-client Resource Pack fallback description.

Earlier documentation described language-specific alias ZIP delivery as an active runtime path. The configuration currently committed on `main` instead points every language-keyed fallback entry to one shared multilingual Resource Pack ZIP.

## Verified Configuration Shape

At the time of this alignment, the configured fallback entries resolve to:

```text
resourcepacks/generated/treasurerun-i18n-pack.zip
```

The configured fallback entries also share the same SHA-1 value.

The shared ZIP contains Minecraft language JSON assets for multiple supported locale mappings.

## Retained Evidence and Current Interpretation

Language-specific ZIP artifacts remain present under `resourcepacks/generated/`, and the Java fallback dispatch classes remain present in the implementation.

These retained artifacts and classes are useful historical/generated evidence, but they do not override the behavior currently defined by `config.yml`.

Therefore, the claim supported by the current evidence is:

> TreasureRun's currently verified vanilla-client fallback route uses one shared multilingual Resource Pack artifact. Language-specific ZIP delivery is not presently claimed as active runtime behavior.

## Documentation Action

This documentation-only change:

- replaces stale current-facing fallback wording with the currently verified runtime contract;
- preserves the previous verification note verbatim under `docs/archive/`;
- leaves Java implementation, `config.yml`, generated ZIP artifacts, SHA-1 values, CI, README, and contributor runtime unchanged.

## Follow-Up Implementation Boundary

A separate implementation pull request is planned to add language-specific Resource Pack delivery for vanilla clients using assets hosted on GitHub Releases.

That future work should be reviewed independently because it changes distribution and runtime routing behavior. Any decision to remove binary data already present in Git history must also remain separate, since rewriting published history has broader consequences for repository consumers.
