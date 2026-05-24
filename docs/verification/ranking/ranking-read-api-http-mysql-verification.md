# Ranking Read API — HTTP to MySQL 8 Verification

## Purpose

This document records the verification boundary for the first read-only Ranking
API vertical slice in TreasureRun.

## Implemented endpoint

```http
GET /api/v1/rankings/all-time?limit=10
```

The endpoint returns ranked public leaderboard entries from `alltime_scores`.
The response DTO deliberately omits player UUID values.

## Architecture under test

```text
HTTP request
  -> Spring Boot REST Controller
  -> RankingReadService
  -> RankingReadRepository using JdbcTemplate
  -> MySQL 8 ranking schema
```

## Automated integration test

`ranking-api/src/test/java/com/flowmari/treasurerun/rankingapi/api/RankingReadApiMySqlIntegrationTest.java`

The test:

1. starts a disposable MySQL 8.0.36 container with Testcontainers;
2. applies the existing plugin-owned `V1`, `V2`, and `V3` ranking migrations;
3. inserts controlled leaderboard fixture rows;
4. starts the actual Spring Boot HTTP application on a random port;
5. issues a real HTTP GET request to the all-time leaderboard endpoint;
6. verifies ranking ordering and the response limit;
7. verifies that the public DTO does not expose UUID values;
8. verifies invalid public input is rejected with HTTP 400.

## Existing write-side evidence

This read API complements the existing repository-level MySQL integration
coverage for the Spigot write path:

- transactional outbox insertion;
- duplicate-event aggregation barrier;
- rollback behaviour when ranking persistence fails.

## Evidence state

Local verification completed before Pull Request publication:

```bash
./gradlew -p ranking-api clean test build --no-daemon
./gradlew clean test build --no-daemon
```

GitHub Actions verification is provided by `.github/workflows/ranking-api-ci.yml`
and must be confirmed on the Pull Request before merge.

## Scope limits

This proof is a read-only API and MySQL integration slice. It does not yet
demonstrate authentication, production hosting, Kubernetes deployment,
observability, PostgreSQL, Redis, jOOQ, or payment-domain behaviour.
