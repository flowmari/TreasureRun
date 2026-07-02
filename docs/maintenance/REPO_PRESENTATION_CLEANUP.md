# Repo presentation cleanup plan

This note tracks small cleanup work before asking for broader alpha feedback.

The goal is not to change gameplay, Java implementation, i18n behavior, language files, ResourcePack assets, or Fabric code. The goal is to make the repository easier to understand for a first-time reader or tester.

## Current state

`v0.1.5-alpha` is published as a focused alpha release for the heartbeat cue audible fix.

## Cleanup candidates

- Keep public README wording neutral and focused on the project status.
- Reduce root-level scratch artifacts from the repository surface.
- Review `_disabled/` and `attic/` separately before deciding whether to remove, archive, or document them.
- Review large research-log style documentation areas separately, especially `docs/i18n_native_review/` and `docs/i18n_batch*`.
- Keep external tester instructions focused on the basic Spigot 1.20.1 playtest path.

## Non-goals

- No Java implementation changes.
- No i18n behavior changes.
- No language file changes.
- No ResourcePack or Fabric changes.
- No production-readiness claim.
- No Paper compatibility claim.
