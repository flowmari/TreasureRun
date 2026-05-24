# ADR-003: Separate Read-Only Ranking API Service Boundary

## Status

Proposed in feature branch pending review and CI verification.

## Context

TreasureRun already persists aggregated ranking state through its Spigot plugin
and verifies the write-side transaction boundary against disposable MySQL 8.

Adding HTTP concerns directly to the Minecraft runtime would couple web-service
lifecycle, dependency management, and public DTO design to gameplay execution.

## Decision

Introduce `ranking-api/` as an independent Spring Boot service within the same
repository.

The service:

- reads the existing plugin-owned ranking schema;
- exposes only read-oriented leaderboard endpoints;
- keeps the Spigot plugin responsible for gameplay and ranking writes;
- keeps the existing SQL migration files as the schema source of truth;
- verifies the API boundary through HTTP-to-MySQL integration tests.

The first vertical slice exposes:

```http
GET /api/v1/rankings/all-time?limit=10
```

## Why the same repository

Keeping the read service in the same repository makes the schema contract visible:
the plugin write path and the API read path can be tested against the same
migration history in one reviewable engineering artifact.

A separate repository would introduce versioning and schema-coordination concerns
before the API boundary has been proven.

## Verification boundary

`RankingReadApiMySqlIntegrationTest` starts disposable MySQL 8, applies the
existing TreasureRun migrations, boots the HTTP application on a random port,
invokes the real endpoint, and verifies:

- leaderboard ordering;
- request validation;
- omission of player UUID values from the public DTO.

## Deliberate limits

This decision does not yet claim:

- production deployment;
- authentication or authorisation;
- database-level read-only credentials;
- asynchronous outbox publication;
- restart-safe delivery;
- financial-service or payment-processing experience.
