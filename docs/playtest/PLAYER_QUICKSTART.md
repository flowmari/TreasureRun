# Player Quickstart

This is the shortest path from a local TreasureRun checkout to a useful first playtest.

The goal is simple:

```text
Can a first-time player start the local server, join the game, start a treasure run, and understand what to do next?
```

The localisation and platform-boundary architecture are documented separately. Start here if you want to experience the game first.

The linked overview below shows the complete first-run flow before the detailed instructions. Screenshots and feature-specific checks are kept on a separate page so this route stays focused.

## Contents

1. [Check the prerequisites](#1-check-the-prerequisites)
2. [Start the local server and wait until the terminal is ready](#2-start-the-local-server-and-wait-until-the-terminal-is-ready)
3. [Add the local server and join](#3-add-the-local-server-and-join)
4. [Run the first playtest](#4-run-the-first-playtest)
5. [Stop or reset the runtime](#5-stop-or-reset-the-runtime)

**Optional follow-up:** [Share first-time feedback](#optional-follow-up-share-first-time-feedback)

## 1. Check the prerequisites

Make sure you have:

- the TreasureRun repository on your computer;
- a terminal opened in the TreasureRun folder;
- Docker Desktop installed and running;
- Minecraft Java Edition 1.20.1 available;
- your exact Minecraft Java player name.

The startup script starts both an isolated MySQL 8 container and the Spigot 1.20.1 server through Docker Compose. You do not need to start MySQL separately.

The first run may take a few minutes while Gradle and Docker prepare the local runtime.

## 2. Start the local server and wait until the terminal is ready

From the repository root, run:

```bash
./scripts/contributor-up.sh YourMinecraftName
```

Example:

```bash
./scripts/contributor-up.sh flowmari
```

Wait until the terminal shows:

```text
Local TreasureRun runtime started.
```

## 3. Add the local server and join

In Minecraft Java Edition 1.20.1, add a local server and enter this address exactly as shown, including the colon:

```text
localhost:25575
```

This is the verified address for the local Docker setup documented in this guide.

Keep **Server Resource Packs** set to **Prompt**, and accept the ResourcePack prompt if Minecraft shows one.

Other client versions are not the verified target for this alpha setup.

## 4. Run the first playtest

Use this shortest first-playtest path:

```text
/lang en
/gamestart normal
```

If no language has been saved yet, `/gamestart normal` may open the language selection GUI first. Choose a language, then start the run.

During the run:

1. follow the treasure-proximity cues;
2. search for and open a treasure chest;
3. notice whether the next action is clear.

The most useful first-playtest question is:

```text
Was it clear what to do next?
```

## 5. Stop or reset the runtime

Stop the local runtime while keeping its world and database volumes:

```bash
./scripts/contributor-down.sh
```

Reset the local runtime completely:

```bash
./scripts/contributor-down.sh --volumes
```

## Optional follow-up: Share first-time feedback

A short result is enough. Please report only what you actually tried and observed:

```text
OS:
localhost:25575 worked: yes / no
First step where I stopped:
First unclear point: none / ...
```

A failed setup report is useful too. TreasureRun is currently an alpha project targeting Spigot 1.20.1.

## Optional features and checkpoints

After completing the core route, see [Optional Playtest Features](OPTIONAL_PLAYTEST_FEATURES.md) for:

- visual checkpoints and screenshots;
- the heartbeat sound check;
- the prepared demo world;
- the Treasure Shop secret trade;
- ranking and MySQL checks;
- the wider player-command reference.
