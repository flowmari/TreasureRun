# TreasureRun Ranking Read API

This directory contains an isolated, read-only Spring Boot service for exposing
leaderboard data already persisted by the TreasureRun Spigot plugin.

## Architectural boundary

- The Spigot plugin remains responsible for gameplay and ranking writes.
- The existing plugin-owned SQL migrations remain the schema source of truth.
- This service reads existing ranking aggregates and does not write gameplay state.
- The first vertical slice exposes all-time leaderboard reads only.

## Endpoint

```http
GET /api/v1/rankings/all-time?limit=10
```

The public DTO intentionally omits player UUID values.

## Verification target

`RankingReadApiMySqlIntegrationTest` starts disposable MySQL 8, applies the
existing TreasureRun ranking migrations, starts the HTTP application on a random
port, invokes the real endpoint, and verifies the returned ranking order and
read-only public boundary.
