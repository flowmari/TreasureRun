# Pre-external root scratch cleanup

This cleanup prepares TreasureRun for one independent external alpha tester by removing root-level temporary scripts and scratch artifacts from the public repository entrance.

## What changed

- Root-level `tmp_*` scripts and scratch artifacts were moved out of the repository entrance.
- Local scratch preservation is kept under `.local-scratch/`, which is intentionally ignored by Git.
- Public repository navigation now starts from the README, source tree, docs, issue templates, and contributor workflow rather than one-off development scripts.
- Public wording is kept focused on technical review, architecture review, and external alpha setup feedback.

## External testing boundary

This cleanup does not publish TreasureRun to SpigotMC, claim Paper compatibility, mutate GitHub Releases, restart Docker runtime, or claim production readiness.

After this cleanup is merged, the next step is to ask one independent Minecraft Java / Spigot user to follow the alpha setup guide and report setup feedback through Issue #24 or a follow-up issue.
