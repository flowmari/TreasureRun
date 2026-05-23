# ADR-002: Bounded Idempotent Transactional Outbox for Ranking Aggregates

## Status

Proposed in feature branch; verification required before merge.

## Context

TreasureRun maintains `season_scores` and `alltime_scores` as ranking aggregates. A terminal gameplay callback can potentially be invoked more than once during one live run. Applying the same score delta twice would corrupt those aggregates.

## Decision

A `GameResultRecorded` event is allocated one `eventId` per live gameplay run. `SeasonScoreRepository` writes that event to `outbox_events` before updating ranking aggregates, within the same JDBC transaction.

`outbox_events.event_id` is unique. When a duplicate terminal callback for the same live run reaches the repository, the duplicate event insert fails before ranking increments are executed. The repository rolls back and reports that no new ranking update was applied.

## Verified by unit tests

- a fresh event writes outbox, weekly aggregate, and all-time aggregate before commit
- a duplicate event id executes no ranking increments
- a downstream persistence failure rolls back the outbox and ranking updates
- nullable best-time and fallback language values remain normalized

## Explicit boundary

This decision protects only:

- `season_scores`
- `alltime_scores`
- their associated `outbox_events` row

This decision does not yet provide:

- asynchronous publication of outbox rows
- Kafka, Redis Streams, or other broker integration
- crash-recovery replay across Minecraft server restarts
- exactly-once protection for the legacy raw `scores` history insertion
- exactly-once protection for `proverb_logs`

These limitations are intentional and should not be overstated.
