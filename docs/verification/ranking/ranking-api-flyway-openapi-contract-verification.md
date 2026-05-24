# Ranking API Flyway and OpenAPI Contract Verification

## Verification target

This verification extends the read-only Ranking API boundary introduced in
ADR-003 without changing the Spigot plugin write path or the existing ranking
migration ownership model.

## Flyway verification

The integration test starts disposable MySQL 8 and allows Flyway to apply the
existing plugin-owned ranking migrations from the test classpath:

- `V1__create_ranking_tables.sql`
- `V2__support_monthly_seasons.sql`
- `V3__create_transactional_outbox.sql`

The test verifies that all three migrations appear in Flyway schema history
before the public read endpoint is exercised.

## OpenAPI verification

The service publishes generated OpenAPI documentation at:

```http
GET /v3/api-docs
```

The integration test verifies:

- `GET /api/v1/rankings/all-time` exists in the OpenAPI document;
- `limit` is documented with default `10`, minimum `1`, and maximum `100`;
- success and invalid-request responses are documented;
- the public DTO includes leaderboard fields;
- UUID values are absent from the public OpenAPI schema.

## Existing API path retained

The existing integration coverage remains in place:

```text
HTTP request
  -> Spring Boot REST Controller
  -> RankingReadService
  -> JdbcTemplate RankingReadRepository
  -> disposable MySQL 8
```

## Scope boundary

This verification demonstrates migration-contract and API-contract checking.
It does not claim PostgreSQL, jOOQ, production deployment, authentication,
observability, or financial transaction processing.
