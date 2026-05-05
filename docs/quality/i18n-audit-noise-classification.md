# i18n Audit Noise Classification

This document records how TreasureRun classifies i18n audit findings.

The goal is not only to pass checks, but to keep translation quality maintainable.

## Classification policy

i18n findings are classified into the following categories:

| Category | Meaning | Action |
|---|---|---|
| Player-visible text | Text that can be shown directly to players during normal gameplay | Translate or rewrite |
| Internal log text | Debug, repository, database, or server-side diagnostic messages | Keep stable or exclude from player-facing quality checks |
| Generated legacy key | Automatically extracted or historical fallback key that is no longer the primary display path | Review and remove/allowlist if unused |
| Minecraft standard asset text | Vanilla Minecraft translation-key content imported into ResourcePack / Fabric Mod assets | Keep key coverage stable; improve translation quality separately |

## Current audit interpretation

Recent suspicious checks detected several classes of findings:

- `QuoteFavoriteStore ... failed`
  - Classified as internal repository / database diagnostic text.
  - These are not primary player-facing gameplay messages.

- `rank_1_2_3_demo_*` Japanese remnants in non-Japanese language files
  - Classified as player-visible or command-facing text.
  - These should be fixed or replaced with locale-appropriate text.

- `quoteFavorite book ... open catalog`
  - Classified as generated legacy command-help remnants.
  - The canonical command help entries already exist under structured i18n keys.
  - These should be reviewed and either translated, removed, or allowlisted if unused.

## Engineering value

This process shows that TreasureRun treats i18n as an operational quality system:

- detect suspicious text
- separate real user-facing issues from internal diagnostic noise
- avoid hiding problems blindly
- keep CI useful by reducing false positives
- keep README and documentation readable for reviewers

This is part of the project's quality-control story, not just a translation task.
