# ADR-004: Flyway Migration Contract and OpenAPI Boundary Verification

## Status

Proposed in local feature branch pending review and CI verification.

## Context

The first Ranking Read API slice proves that an HTTP request can traverse
Spring Boot controller, service, JDBC repository, and disposable MySQL 8 while
keeping gameplay writes in the Spigot plugin.

The initial integration test applied the plugin-owned SQL migration files
through test-local statement execution. It also verified endpoint behaviour,
but did not publish a machine-readable API contract.

## Decision

Keep the Spigot plugin as the owner of production ranking schema migrations,
while making the API verify that shared schema contract through Flyway during
disposable-database integration tests.

Expose generated OpenAPI v3 documentation from the Ranking API and verify the
runtime contract through automated HTTP assertions.

## Consequences

- Migration order and schema compatibility are verified through Flyway.
- The API exposes a machine-readable contract at `/v3/api-docs`.
- The public endpoint documents its `limit` boundary and DTO response.
- UUID omission is verified both in runtime JSON responses and in the OpenAPI schema.
- No production schema ownership is transferred from the plugin to the read API.

## Deliberate limits

This decision does not yet introduce:

- PostgreSQL;
- jOOQ;
- Prometheus or Grafana instrumentation;
- API authentication or authorisation;
- a production deployment claim;
- payment-processing or financial-domain behaviour.
