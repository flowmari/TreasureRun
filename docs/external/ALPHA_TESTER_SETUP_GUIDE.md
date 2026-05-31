# Alpha Tester Setup Guide

TreasureRun is an alpha-stage Minecraft Spigot 1.20.1 mini-game plugin.

This guide is for external testers who want to run the project locally, try the gameplay, and report reproducible feedback.

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

## Start a local test server

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

Use Minecraft Java Edition 1.20.1 and connect to:

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
