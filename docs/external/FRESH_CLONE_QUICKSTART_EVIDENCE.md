# Fresh Clone QuickStart Evidence

## Current status

Status: **PASS**

This document records a real fresh-clone measurement for the contributor QuickStart.

This evidence was added after PR #18, because PR #18 intentionally created the external alpha readiness surface without claiming that the QuickStart had already been measured.

## Measurement summary

| Item | Result |
|---|---|
| Repository | `https://github.com/flowmari/TreasureRun.git` |
| Main commit measured | `e1151c408dab99bac0b717b3b2d6a3f4b8f131a9` |
| Fresh clone commit | `e1151c408dab99bac0b717b3b2d6a3f4b8f131a9` |
| Operator name used | `flowmari` |
| Clone time | 30s |
| Historical startup command recorded at measurement time | `TREASURERUN_OPS=flowmari ./scripts/contributor-up.sh` |
| Current external-feedback follow-up startup command | `./scripts/contributor-up.sh flowmari` |
| Startup exit code | `0` |
| Startup measured time | 39s |
| Shutdown command | `./scripts/contributor-down.sh` |
| Shutdown exit code | `0` |

## Result

The documented fresh-clone contributor startup command completed successfully in this local measurement.

The historical command above is preserved as evidence from the measurement date. The current tester-facing setup path is argument-first: `./scripts/contributor-up.sh YourMinecraftName`.

## Evidence procedure

The measurement used a clean temporary clone outside the working repository:

1. Clone `https://github.com/flowmari/TreasureRun.git`.
2. Check out `main`.
3. Verify the fresh clone commit matches the current local `origin/main`.
4. Run the documented contributor startup command.
5. Capture Docker state and recent logs.
6. Stop the contributor runtime.
7. Record the result in this document.

## Important boundary

This evidence proves only the local contributor QuickStart behavior on the measured machine.

It does not claim:

- Paper compatibility;
- SpigotMC publication readiness;
- external alpha tester success;
- production deployment readiness;
- database migration repair completion;
- translation quality completion.

## Raw evidence location

The raw local evidence was written outside the repository during measurement:

`/tmp/treasurerun_pr19_fresh_clone_20260531_164410`

The repository intentionally stores this concise summary instead of committing local machine logs.
