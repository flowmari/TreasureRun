# TreasureRun Ranking Read API

This directory contains an isolated, read-only Spring Boot service for exposing
leaderboard data already persisted by the TreasureRun Spigot plugin.

## Architectural boundary

- The Spigot plugin remains responsible for gameplay and ranking writes.
- The existing plugin-owned SQL migrations remain the schema source of truth.
- This service reads existing ranking aggregates and does not write gameplay state.
- The API project verifies compatibility with the shared migration history through Flyway in disposable-database integration tests.

## Endpoint

```http
GET /api/v1/rankings/all-time?limit=10
```

The public DTO intentionally omits player UUID values.

## OpenAPI contract

The service exposes generated OpenAPI documentation at:

```http
GET /v3/api-docs
```

`RankingReadApiMySqlIntegrationTest` verifies that the runtime-generated
contract contains the all-time endpoint, the documented `limit` boundary,
successful/error responses, and a public DTO schema without UUID exposure.

## Flyway verification boundary

The API does not introduce a second production migration source of truth.
Instead, integration tests place the plugin-owned `V1` to `V3` SQL migrations
on the test classpath and let Flyway apply and validate them against disposable
MySQL 8 before the HTTP request path is exercised.

## Verified request path

```text
HTTP request
  -> Spring Boot REST Controller
  -> RankingReadService
  -> JdbcTemplate RankingReadRepository
  -> disposable MySQL 8
```
