# TreasureRun Documentation Index

This directory contains both current design documentation and historical verification evidence.  
Start with the current-facing documents below. The large audit and batch folders are retained as evidence, but they are not the recommended first reading path.

## Start Here

| Document | Purpose |
|---|---|
| [`../README.md`](../README.md) | Contributor-first project overview, local runtime, contribution entry points, and CI summary. |
| [`ARCHITECTURE_GUIDE.md`](ARCHITECTURE_GUIDE.md) | Current high-level architecture map for gameplay, i18n, ResourcePack, Fabric, and backend boundaries. |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | More detailed architecture notes for the Spigot plugin and optional backend evidence. |
| [`COMMANDS.md`](COMMANDS.md) | Player/admin command surface for the Spigot plugin. |
| [`GAME_DESIGN.md`](GAME_DESIGN.md) | Creative vision, setting, worldbuilding, and gameplay atmosphere. |

## Key Engineering Evidence

| Area | Evidence |
|---|---|
| Platform-boundary i18n | [`verification/resource-pack/server-side-resource-pack-i18n.md`](verification/resource-pack/server-side-resource-pack-i18n.md) |
| Release-hosted fallback ResourcePacks | [`verification/i18n/reproducible-fallback-resource-pack-builds.md`](verification/i18n/reproducible-fallback-resource-pack-builds.md) |
| Gothic / historical locale expansion | [`verification/i18n/gothic-runtime-evidence.md`](verification/i18n/gothic-runtime-evidence.md), [`verification/i18n/historical-germanic-locales-final-evidence.md`](verification/i18n/historical-germanic-locales-final-evidence.md) |
| Ranking backend boundary | [`adr/ADR-003-ranking-read-api-service-boundary.md`](adr/ADR-003-ranking-read-api-service-boundary.md) |
| Flyway / OpenAPI / Testcontainers evidence | [`adr/ADR-004-ranking-api-flyway-openapi-contract.md`](adr/ADR-004-ranking-api-flyway-openapi-contract.md), [`verification/ranking/ranking-api-flyway-openapi-contract-verification.md`](verification/ranking/ranking-api-flyway-openapi-contract-verification.md) |
| Transactional outbox design | [`adr/ADR-002-ranking-bounded-idempotent-outbox.md`](adr/ADR-002-ranking-bounded-idempotent-outbox.md), [`verification/ranking/bounded-idempotent-outbox-mysql-verification.md`](verification/ranking/bounded-idempotent-outbox-mysql-verification.md) |

## Historical Evidence and Audit Dumps

The following folders are intentionally retained, but they are not the main reviewer path:

- `i18n_audit/`
- `i18n_batch07/`
- `i18n_batch08/`
- `i18n_native_review/`
- `i18n_triage/`
- `translation-review/`
- `verification/`

Some files in these folders describe earlier implementation states or pre-release fallback-routing designs. When a file is historical, prefer the current-facing README, architecture guide, and config/test evidence for the current state.

## Current Scope Boundaries

TreasureRun is currently best read as:

- a Java/Spigot gameplay plugin;
- a platform-boundary i18n project using YAML, ProtocolLib, ResourcePack assets, and optional Fabric runtime language sync;
- a project with optional backend evidence for ranking read APIs, Flyway migrations, OpenAPI contract checks, MySQL, and Testcontainers;
- an evolving OSS portfolio where heavy generated artifacts should be delivered through GitHub Releases rather than kept in the source tree.

Known follow-up work is intentionally tracked separately, including the Docker/MySQL V3 transactional-outbox migration warning and ProtocolLib packet-boundary verification.
