-- TreasureRun ranking transactional outbox schema
-- MySQL 8.0+
--
-- Correctness boundary:
--   - Protects season_scores / alltime_scores from duplicate processing of
--     the same terminal callback while the gameplay run retains one event_id.
--   - Persists an outbox record in the same transaction as the ranking update.
--   - Does not yet provide asynchronous publication or crash-recovery replay.
--   - Does not fold the legacy raw scores insert into this transaction.

CREATE TABLE IF NOT EXISTS outbox_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id CHAR(36) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  payload_json JSON NOT NULL,
  occurred_at TIMESTAMP(3) NOT NULL,
  published_at TIMESTAMP(3) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uniq_outbox_event_id (event_id),
  KEY idx_outbox_unpublished (published_at, id),
  KEY idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
