# External Alpha Readiness Checklist

This checklist tracks what must be true before TreasureRun is promoted beyond early-alpha repository status.

## Completed

- Contributor-first README exists.
- Local contributor runtime command exists.
- Standard community files exist:
  - `LICENSE`
  - `CONTRIBUTING.md`
  - `CODE_OF_CONDUCT.md`
  - `SECURITY.md`
- Bug report and translation request issue templates exist.
- Release-hosted per-language ResourcePack fallback routes exist.
- Obsolete per-language ZIP binaries are no longer tracked in source control.
- The retained shared multilingual ResourcePack ZIP and SHA1 remain tracked.
- CI checks cover build, i18n YAML safety, i18n reference integrity, ResourcePack expansion integrity, and optional ranking API integration.

## Still required before broader alpha exposure

- Record fresh-clone QuickStart evidence.
- Clean or intentionally archive root scratch files.
- Open beginner-safe GitHub issues.
- Gather at least one external tester report.
- Verify Paper compatibility before claiming Paper support.
- Prepare a SpigotMC Resource page only after alpha tester setup is proven.
- Convert translation-quality warnings into scoped translation review issues.

## Not part of this PR

- DB migration V3 repair.
- ProtocolLib packet warning repair.
- Paper compatibility claim.
- SpigotMC Resource publication.
- Full translation quality rewrite.
- Public marketing launch.

## Fresh Clone and Good First Issue gate

Before broader alpha exposure, the project should have:

- a measured Fresh Clone QuickStart transcript;
- at least three real GitHub issues suitable for first-time contributors;
- one issue focused on translation wording cleanup;
- one issue focused on documentation clarity;
- one issue focused on a small gameplay configuration review;
- a clear path for alpha testers to report feedback.

PR #18 prepares these surfaces, but it does not complete external validation by itself.
