# Alpha Tester Setup Guide

TreasureRun is an alpha-stage Minecraft Spigot 1.20.1 mini-game plugin.

This guide is for testers who want to run the project locally, try the gameplay, and report feedback.

## Start here: what am I testing?

If you are a Minecraft player rather than a programmer, start here.

TreasureRun has a few different parts, but you do not need to understand all of them before trying the basic alpha.

- **Server plugin**: this is the main part of TreasureRun. It runs on a local Spigot 1.20.1 server.
- **Mod**: many Minecraft players know this word. In this project, the optional Fabric mod is only for advanced client-side language-sync testing. You do not need it for the basic alpha test.
- **ResourcePack**: Minecraft may ask you to accept a server ResourcePack when you join. This helps show language-related Minecraft UI assets correctly.
- **Local test server**: this guide starts a Minecraft server on your own computer so you can test safely without joining a public server.

For the first alpha test, focus on this simple goal:

1. Start the local server.
2. Open Minecraft Java Edition 1.20.1.
3. Connect to the local server.
4. Accept the ResourcePack prompt if it appears.
5. Tell us which step was confusing.

In short: TreasureRun is mainly a **server plugin**, not a normal client-side mod. The ResourcePack and optional Fabric mod support the language/i18n experiment, but the first test starts with the local Spigot server.

## What this project demonstrates

TreasureRun is not only a treasure-hunt mini-game. It demonstrates a platform-boundary i18n architecture for Minecraft:

- Spigot plugin messages are handled on the server side.
- ProtocolLib observes and rewrites reachable translatable packet content.
- ResourcePack language assets cover Minecraft standard translation keys.
- An optional Fabric client mod supports runtime language switching and resource reload.
- Pure Java i18n logic is kept testable without depending on Bukkit, ProtocolLib, Fabric, or Minecraft runtime APIs.

## Requirements

For the contributor runtime:

- macOS, Linux, or Windows with a Unix-like shell
- Docker Desktop
- Java 17
- Git
- Minecraft Java Edition 1.20.1

## Optional: use the prepared demo world

A freshly generated world is enough for a basic startup test. To test the same kind of gameplay environment shown in the README demo, use the prepared demo-world ZIP before starting the local server.

1. Download the demo-world ZIP from the pre-release testing asset:

   ```text
   https://github.com/flowmari/TreasureRun/releases/download/demo-world-external-test-20260611/treasurerun-demo-world-for-external-tester-20260611_181531.zip
   ```

2. Extract it at the repository root.
3. Confirm this file exists:

   ```text
   .local/demo-world/world/level.dat
   ```

4. Continue with the local server command below.

The prepared demo world is intentionally kept out of Git. For the tester-facing highlights, see [`../demo-world/WHAT_TO_LOOK_FOR.md`](../demo-world/WHAT_TO_LOOK_FOR.md). For the creative vision and worldbuilding notes, see [`../GAME_DESIGN.md`](../GAME_DESIGN.md).

## Start a local test server

This step starts a local Spigot server and installs the TreasureRun plugin into that server.

From the repository root:

```bash
TREASURERUN_OPS=YourMinecraftName ./scripts/contributor-up.sh
```

Example:

```bash
TREASURERUN_OPS=flowmari ./scripts/contributor-up.sh
```

The script builds the plugin and starts an isolated Docker-based local server.

## Connect from Minecraft

Open Minecraft Java Edition 1.20.1 on the same machine and connect to the local test server.

Use:

```text
localhost:25575
```

## Basic commands to try

```text
/lang
/gamestart normal
/gameRank
/rank
```

## ResourcePack behavior

Minecraft may show a server ResourcePack prompt when you connect.

For this alpha test, please accept the ResourcePack prompt if it appears.

TreasureRun uses a multilingual ResourcePack to cover Minecraft standard translation-key assets where packet rewriting alone is not enough.

If Minecraft asks whether to accept the server ResourcePack, accept it for the full i18n experience.

## What to test

Please report:

- whether the server starts successfully from a clean clone;
- whether Minecraft can connect to `localhost:25575`;
- whether `/lang` opens the language menu;
- whether `/gamestart normal` starts a playable run;
- whether ResourcePack prompts or language switching behave unexpectedly;
- whether any visible text is confusing, untranslated, or mixed-language;
- whether the server logs show clear errors.

## What not to claim yet

This alpha does not yet claim:

- Paper compatibility;
- production server hardening;
- native-level translation quality for every experimental locale;
- full control over every Minecraft client-side screen;
- financial-grade backend behavior.

## Stop the local server

```bash
./scripts/contributor-down.sh
```

To remove the contributor runtime volumes as well:

```bash
./scripts/contributor-down.sh --volumes
```

## How to report feedback

Please open a GitHub issue and include:

- operating system;
- Java version;
- Docker version;
- Minecraft version;
- exact command used;
- screenshots if relevant;
- server logs or error text;
- steps to reproduce.

## Known limitations for alpha testers

This alpha setup is intentionally narrow.

TreasureRun currently targets **Spigot 1.20.1**. Paper may work, but Paper compatibility should not be claimed until it is tested separately and recorded as evidence.

The local setup is meant to help testers try the gameplay loop, basic commands, ranking behavior, and language/resource-pack behavior. It is not yet a public production server setup.

If something fails, useful feedback includes:

- the operating system;
- Java version;
- Minecraft client version;
- whether the ResourcePack prompt appeared;
- the command that failed;
- the relevant server log lines;
- screenshots or short screen recordings when possible.
