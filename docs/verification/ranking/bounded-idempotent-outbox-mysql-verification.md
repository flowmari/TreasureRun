# Bounded Idempotent Ranking Outbox: MySQL 8 Verification

## Scope

This document records verification of the bounded idempotent ranking-outbox change introduced in implementation commit `df8534d` on Pull Request #4.

The verified path covers:

- `GameResultRecorded`
- `outbox_events`
- `season_scores`
- `alltime_scores`
- repeated handling attempts that reuse the same per-live-run `event_id`

## Verification model

Two complementary checks were used:

1. Java unit tests verify repository behavior, including transaction ordering, duplicate-event handling and rollback behavior.
2. A disposable MySQL 8 container verifies the real migration schema, unique-key enforcement and transaction rollback behavior.

When this document was first written, database verification executed SQL statements that mirrored the transaction flow; it did not execute the Java repository itself. Pull Request #6 subsequently added an automated Testcontainers-backed integration test that invokes the actual repository implementation against disposable MySQL 8.

## Environment

| Item | Result |
| --- | --- |
| Date | 2026-05-23 JST |
| Database | Disposable Docker container using `mysql:8.0` |
| Local build for implementation commit | `./gradlew clean test build --no-daemon` succeeded |
| Pull Request checks | GitHub Actions checks passed |
| Repository mutation during database verification | None |
| Disposable container cleanup | Confirmed |

## Verified evidence

### Migration execution

The following migrations executed successfully on MySQL 8:

- `V1__create_ranking_tables.sql`
- `V2__support_monthly_seasons.sql`
- `V3__create_transactional_outbox.sql`

The resulting tables were confirmed:

- `seasons`
- `season_scores`
- `alltime_scores`
- `outbox_events`

### Unique event-id barrier

The `outbox_events` table was confirmed to contain the unique constraint:

`UNIQUE KEY uniq_outbox_event_id (event_id)`

A first ranking result and its outbox row committed with this observed state:

`outbox_count|weekly_score|alltime_score=1|100|100`

A repeated insertion using the same `event_id` was rejected by MySQL. The aggregate state remained unchanged:

`outbox_count|weekly_score|alltime_score=1|100|100`

Together with the Java unit test that confirms aggregate updates are not executed after duplicate outbox insertion, this supports the bounded duplicate-terminal-callback guarantee.

### Rollback behavior

A second distinct outbox event and ranking changes were applied inside a transaction and then rolled back.

Observed state after rollback:

`second_event_count|weekly_score|alltime_score=0|100|100`

This confirms that partial second-result state is not retained after rollback.

### Automated Java-to-MySQL integration coverage

Pull Request #6 added `src/test/java/plugin/rank/SeasonScoreRepositoryMySqlIntegrationTest.java`, together with test-scoped Testcontainers dependencies in `build.gradle`.

The automated test:

- starts a disposable `mysql:8.0.36` container
- applies the existing `V1`, `V2` and `V3` migration scripts
- invokes the actual `SeasonScoreRepository.addWeeklyAndAllTime(...)` Java/JDBC path
- verifies committed state in `outbox_events`, `season_scores` and `alltime_scores`
- verifies that reusing the same `event_id` does not double-count ranking aggregates
- triggers an all-time write failure using a real database constraint and verifies rollback of the outbox and weekly writes

The integration-test change was merged on `main` in Pull Request #6, and GitHub Actions completed successfully on the resulting main commit.

This closes the earlier proof gap between database-level transaction verification and automated execution of the actual Java repository implementation against MySQL.

## Explicit non-claims

This verification does not claim:

- asynchronous outbox publication
- message-broker integration
- crash-recovery replay across server restarts
- exactly-once protection for raw `scores` history insertion
- exactly-once protection for `proverb_logs`

## Conclusion

For the explicitly bounded ranking-aggregation path, the change is supported by unit tests, automated Java-to-MySQL integration testing with Testcontainers, GitHub Actions checks, and MySQL 8 constraint and rollback verification.

The supported claim remains deliberately narrow: when repeated handling reuses the same per-live-run event identifier, duplicate ranking aggregation is prevented, and that behavior is now exercised automatically through the actual Java repository implementation against disposable MySQL 8.
