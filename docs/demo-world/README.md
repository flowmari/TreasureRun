# Demo World Setup Guide

This guide explains how to prepare a local TreasureRun demo world so reviewers and external testers can see the intended gameplay atmosphere.

TreasureRun can run from a fresh local contributor runtime, but a newly generated world does not include the prepared demo environment used in the README gameplay video, such as:

- llamas and wandering-trader style visual scenery
- transparent cyan/green marker blocks
- yellow light effects
- amber trail-style visual effects
- prepared treasure-hunt locations

The goal of this guide is to make the gameplay demo experience reproducible without committing large Minecraft world files to Git.

## Why the demo world is not committed

Minecraft world folders can become large, noisy, and difficult to review in pull requests. They may also contain local server state that is not useful for source review.

For that reason, TreasureRun keeps demo-world data outside Git and uses a local, ignored workspace:

```text
.local/demo-world/
```

This keeps the repository lightweight while still documenting how an external tester can reproduce the intended demo experience.

## External tester quick path

If you are testing the prepared demo world from the pre-release ZIP, use this path:

1. Download the demo-world ZIP from the pre-release testing asset:

   ```text
   https://github.com/flowmari/TreasureRun/releases/download/demo-world-external-test-20260611/treasurerun-demo-world-for-external-tester-20260611_181531.zip
   ```

2. Extract it at the repository root.
3. Confirm this file exists:

   ```text
   .local/demo-world/world/level.dat
   ```

4. Start the contributor runtime:

   ```bash
   TREASURERUN_OPS=YourMinecraftName ./scripts/contributor-up.sh
   ```

5. Join the local server from Minecraft Java Edition 1.20.1:

   ```text
   localhost:25575
   ```

6. Once you are in game, try `/lang`, `/gamestart normal`, and `/gameRank`.

For the tester-facing highlights, see [`WHAT_TO_LOOK_FOR.md`](WHAT_TO_LOOK_FOR.md).

ZIP filename:

```text
treasurerun-demo-world-for-external-tester-20260611_181531.zip
```

SHA256:

```text
0e86e9d5e7fd98f1fc6728d02e3cea2b85672189368991cc678f9b446b935b1a
```

## Recommended workflow

1. Prepare or verify the authored local demo world in Minecraft.
2. Export/copy that world into `.local/demo-world/world`.
3. Start the local TreasureRun contributor runtime.
4. Import or mount the prepared world into the runtime environment.
5. Ask an external tester to follow the documented setup path and report whether the demo experience is clear.

## Exporting a local demo world

Use the helper script:

```bash
./scripts/demo-world-setup.sh /path/to/source/world
```

Example on macOS:

```bash
./scripts/demo-world-setup.sh "$HOME/Library/Application Support/minecraft/saves/TreasureRunDemo"
```

The script copies the source world into:

```text
.local/demo-world/world
```

The copied world is intentionally ignored by Git.

## What reviewers should see

When the demo world is connected to the local runtime, reviewers should be able to see the same kind of gameplay atmosphere shown in the README demo:

- a prepared treasure-hunt area
- visible reward effects
- score and ranking feedback
- multilingual/i18n behavior where applicable
- a clear game loop without needing to build the scene manually
- tester-facing highlights documented in [`WHAT_TO_LOOK_FOR.md`](WHAT_TO_LOOK_FOR.md)

## Contributor runtime integration

When `.local/demo-world/world` exists, `scripts/contributor-up.sh` calls `scripts/contributor-demo-world-sync.sh` before starting the local contributor runtime.

The sync helper copies the ignored local demo world into:

```text
spigot-data/world
```

On the first sync, if an existing runtime world is present and was not created by the demo-world sync helper, it is moved into a timestamped safety backup under:

```text
.local/demo-world/runtime-backups/
```

After that, repeated runs refresh the synced demo world instead of creating a new backup every time.

To skip demo-world sync for a fresh generated world, run:

```bash
TREASURERUN_USE_DEMO_WORLD=0 TREASURERUN_OPS=YourMinecraftName ./scripts/contributor-up.sh
```

This keeps Minecraft world data out of Git while giving external testers a reproducible gameplay environment.

## Verification evidence

Maintainer-run runtime verification is recorded in:

```text
docs/demo-world/runtime-verification.md
```

That note documents local end-to-end verification only. Independent external tester validation should be recorded separately after a tester follows the setup path.
