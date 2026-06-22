# Demo World Runtime Verification Notes

This note records a maintainer-run verification of the TreasureRun demo-world runtime path.

It is intentionally limited to local verification. It does **not** claim independent external tester validation yet.

## Verification date

2026-06-11

## What was verified

The following path was verified locally:

1. The repository was synced to `main`.
2. The working tree was clean before the test.
3. The local demo world existed under `.local/demo-world/world`.
4. `.local/demo-world/world` was ignored by Git.
5. No Minecraft world data was tracked by Git.
6. `scripts/contributor-up.sh` invoked `scripts/contributor-demo-world-sync.sh`.
7. The prepared local demo world was copied into the contributor runtime workspace at `spigot-data/world`.
8. The contributor runtime built successfully.
9. Docker Compose started the isolated MySQL and Spigot services.
10. The runtime world contained `level.dat`.
11. The runtime world contained the demo-world sync marker file.
12. No tracked files changed during the runtime verification.

## Historical key command

```bash
TREASURERUN_OPS=flowmari ./scripts/contributor-up.sh
```

This command is preserved as historical evidence from the original local verification. The current external-feedback follow-up setup path is argument-first:

```bash
./scripts/contributor-up.sh flowmari
```

## Observed result

The runtime reported:

```text
DONE: contributor runtime will use the local demo world.
```

The default Gradle build completed successfully:

```text
BUILD SUCCESSFUL
```

The local contributor runtime started and printed the expected connection target:

```text
Connect from Minecraft Java Edition 1.20.1:
  localhost:25575
```

The synced runtime world was present:

```text
spigot-data/world
```

The copied demo-world workspace remained ignored by Git:

```text
.local/demo-world/world
```

## Git safety boundary

Minecraft world data is intentionally excluded from Git.

The verified design keeps world data in ignored runtime/local paths:

```text
.local/demo-world/world
spigot-data/world
.local/demo-world/runtime-backups/
```

The repository tracks only the documentation and helper scripts required to reproduce the setup path.

## Current status

This is now a maintainer-verified local runtime path.

The next validation step is independent external tester feedback:

- Ask one external tester to follow the setup path.
- Confirm whether they can start the contributor runtime.
- Confirm whether the demo-world atmosphere is visible in Minecraft Java Edition 1.20.1.
- Record their feedback in a GitHub issue or PR comment.
- Do not rewrite that feedback into a stronger claim than what the tester actually confirms.

## Career-facing interpretation

This verification demonstrates that TreasureRun is not only a game demo. It also has a reviewer-friendly runtime path:

- visible README gameplay preview
- documented demo-world setup
- safe runtime sync into the contributor environment
- Git hygiene around large Minecraft world data
- CI-backed pull-request workflow
- clear boundary between maintainer-local verification and independent external validation
