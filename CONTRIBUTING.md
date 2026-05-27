# Contributing to TreasureRun

Thank you for taking the time to contribute to TreasureRun.

## Start Here

For most gameplay, documentation, and translation changes, run:

```bash
./gradlew clean build
```

Docker and MySQL are **not required** for the default build.

When your change touches the database boundary or the ranking API, run the optional Docker-backed integration tests:

```bash
./gradlew integrationTest
./gradlew -p ranking-api integrationTest
```

The project keeps this deeper verification available without requiring it for unrelated day-to-day contributions.

## Good First Contribution Areas

Good first contributions include:

- improving documentation;
- reviewing or improving plugin-owned translations;
- reporting reproducible gameplay or language issues;
- improving tests for the pure Java localization layer;
- improving local development instructions.

## Development Setup

For ordinary Java, documentation, and translation contributions, you need:

- Java 17
- the Gradle wrapper included in this repository

Validate ordinary changes with:

```bash
./gradlew clean build
```

## Optional Local Game Runtime

To start an isolated local Spigot server with MySQL provided automatically through Docker:

```bash
TREASURERUN_OPS=YourMinecraftName ./scripts/contributor-up.sh
```

Then connect from Minecraft Java Edition 1.20.1:

```text
localhost:25575
```

This contributor runtime uses its own Docker project, its own data volumes, and a separate default Minecraft port so that it does not interfere with an existing maintainer setup.

Stop it while keeping local runtime data:

```bash
./scripts/contributor-down.sh
```

Remove the runtime and its local data volumes:

```bash
./scripts/contributor-down.sh --volumes
```

## When to Run the Integration Tests

Run the Docker-backed integration tests when changing:

- `ranking-api/`;
- Flyway migrations;
- ranking repositories or database-boundary code;
- MySQL configuration behavior;
- related Gradle or GitHub Actions configuration.

Commands:

```bash
./gradlew integrationTest
./gradlew -p ranking-api integrationTest
```

## Architecture Boundaries

Please keep the current responsibilities clear when contributing or documenting the project:

- the Spigot plugin runs gameplay and owns ranking writes;
- `ranking-api/` is a separate read-only REST API for leaderboard data;
- the optional Fabric mod handles client-side language switching and resource reload;
- pure packet-localization logic remains separate from platform-dependent adapters.

The Fabric mod does not currently send treasure-event writes to `ranking-api`.

## Translation Contributions

For translation changes, please include:

- the target language or locale;
- the affected file or translation key;
- the current wording;
- the proposed wording;
- a short reason for the change;
- a screenshot or reproduction steps when the change affects visible UI.

## Pull Request Checklist

Before opening a pull request:

- keep the change focused;
- avoid unrelated formatting or generated-file changes;
- run the relevant validation command;
- explain what changed and why;
- include screenshots or terminal evidence for visible runtime behavior.

The previous contribution guide is preserved for reference at:

- [`docs/archive/CONTRIBUTING_BEFORE_CONTRIBUTOR_ONRAMP.md`](docs/archive/CONTRIBUTING_BEFORE_CONTRIBUTOR_ONRAMP.md)

Thank you for helping make TreasureRun easier to play, understand, and extend.
