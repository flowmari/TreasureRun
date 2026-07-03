# Repo presentation cleanup plan

This note tracks small cleanup work before asking for broader alpha feedback.

The goal is not to change gameplay, Java implementation, i18n behavior, language files, ResourcePack assets, or Fabric code. The goal is to make the repository easier to understand for a first-time reader or tester.

## Current state

`v0.1.5-alpha` is published as a focused alpha release for the heartbeat cue audible fix.

## Cleanup candidates

- Keep public README wording neutral and focused on the project status.
- Reduce root-level scratch artifacts from the repository surface.
- `_disabled/` and `attic/` were removed in PR #98 after a scoped deletion review.
- The architecture tradeoff summary was moved under `docs/architecture/` in PR #99 without changing the file content.
- Large research-log style documentation areas formerly under `docs/i18n_native_review/` and `docs/i18n_batch*` are archived under `docs/archive/i18n-research-logs/`, preserving the working history while keeping the main docs path focused.
- Keep external tester instructions focused on the basic Spigot 1.20.1 playtest path.

## Non-goals

- No Java implementation changes.
- No i18n behavior changes.
- No language file changes.
- No ResourcePack or Fabric changes.
- No production-readiness claim.
- No Paper compatibility claim.
