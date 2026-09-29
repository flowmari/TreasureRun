-- TreasureRun DB-H3C legacy runtime-schema adoption
-- MySQL 8.0+
-- V1/V2/V3 are immutable.
-- Forward-only, rerunnable, non-destructive, fail-closed.

CREATE TABLE IF NOT EXISTS scores (
  id INT AUTO_INCREMENT PRIMARY KEY,
  uuid VARCHAR(36) NULL,
  player_name VARCHAR(50) NOT NULL,
  score INT NOT NULL,
  time BIGINT NOT NULL,
  difficulty VARCHAR(10) NOT NULL,
  lang_code VARCHAR(10) NOT NULL DEFAULT 'ja',
  played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_scores_played_at (played_at),
  INDEX idx_scores_diff_time (difficulty, time),
  INDEX idx_scores_uuid_played (uuid, played_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @tr_h3c_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE scores ADD COLUMN uuid VARCHAR(36) NULL',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='scores' AND COLUMN_NAME='uuid'
);
PREPARE tr_h3c_stmt FROM @tr_h3c_sql;
EXECUTE tr_h3c_stmt;
DEALLOCATE PREPARE tr_h3c_stmt;

SET @tr_h3c_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE scores ADD COLUMN lang_code VARCHAR(10) NOT NULL DEFAULT ''ja''',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='scores' AND COLUMN_NAME='lang_code'
);
PREPARE tr_h3c_stmt FROM @tr_h3c_sql;
EXECUTE tr_h3c_stmt;
DEALLOCATE PREPARE tr_h3c_stmt;

SET @tr_h3c_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE scores ADD COLUMN played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='scores' AND COLUMN_NAME='played_at'
);
PREPARE tr_h3c_stmt FROM @tr_h3c_sql;
EXECUTE tr_h3c_stmt;
DEALLOCATE PREPARE tr_h3c_stmt;

SET @tr_h3c_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE scores ADD INDEX idx_scores_played_at (played_at)',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='scores' AND INDEX_NAME='idx_scores_played_at'
);
PREPARE tr_h3c_stmt FROM @tr_h3c_sql;
EXECUTE tr_h3c_stmt;
DEALLOCATE PREPARE tr_h3c_stmt;

SET @tr_h3c_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE scores ADD INDEX idx_scores_diff_time (difficulty, time)',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='scores' AND INDEX_NAME='idx_scores_diff_time'
);
PREPARE tr_h3c_stmt FROM @tr_h3c_sql;
EXECUTE tr_h3c_stmt;
DEALLOCATE PREPARE tr_h3c_stmt;

SET @tr_h3c_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE scores ADD INDEX idx_scores_uuid_played (uuid, played_at)',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='scores' AND INDEX_NAME='idx_scores_uuid_played'
);
PREPARE tr_h3c_stmt FROM @tr_h3c_sql;
EXECUTE tr_h3c_stmt;
DEALLOCATE PREPARE tr_h3c_stmt;

CREATE TABLE IF NOT EXISTS proverb_logs (
  id INT NOT NULL AUTO_INCREMENT,
  player_uuid VARCHAR(36) NOT NULL,
  player_name VARCHAR(64) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  difficulty VARCHAR(16) NOT NULL,
  lang VARCHAR(16) NOT NULL,
  quote_text TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_player_uuid_created_at (player_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Normalize the narrower legacy runtime-owned proverb_logs schema.
-- These are widening changes only and are safe to rerun if V4 was partially applied
-- before schema_migrations could record completion.
ALTER TABLE proverb_logs
  MODIFY COLUMN player_name VARCHAR(64) NOT NULL,
  MODIFY COLUMN outcome VARCHAR(32) NOT NULL,
  MODIFY COLUMN difficulty VARCHAR(16) NOT NULL,
  MODIFY COLUMN lang VARCHAR(16) NOT NULL;

CREATE TABLE IF NOT EXISTS favorite_quotes (
  id INT NOT NULL AUTO_INCREMENT,
  player_uuid VARCHAR(36) NOT NULL,
  quote_hash VARCHAR(64) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  difficulty VARCHAR(16) NOT NULL,
  lang VARCHAR(16) NOT NULL,
  quote_text TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_quote (player_uuid, quote_hash),
  INDEX idx_player_uuid_created_at (player_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @tr_h3c_sql = (
  SELECT IF(COUNT(*) = 1,
    'INSERT IGNORE INTO favorite_quotes (player_uuid, quote_hash, outcome, difficulty, lang, quote_text, created_at) SELECT player_uuid, quote_hash, outcome, difficulty, lang, quote_text, created_at FROM proverb_favorites',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='proverb_favorites'
);
PREPARE tr_h3c_stmt FROM @tr_h3c_sql;
EXECUTE tr_h3c_stmt;
DEALLOCATE PREPARE tr_h3c_stmt;
