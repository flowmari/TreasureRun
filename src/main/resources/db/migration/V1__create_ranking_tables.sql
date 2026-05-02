-- TreasureRun ranking persistence schema
-- MySQL 8.0+
--
-- Purpose:
--   - Store weekly ranking scores in season_scores
--   - Store all-time ranking scores in alltime_scores
--   - Track score, wins, best clear time, and selected language code
--   - Link weekly scores to seasons through a foreign key

CREATE TABLE IF NOT EXISTS seasons (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  season_type VARCHAR(32) NOT NULL DEFAULT 'WEEKLY',
  year INT NULL,
  week INT NULL,
  season_key VARCHAR(64) NULL,
  starts_at DATETIME NULL,
  ends_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_season_type_year_week (season_type, year, week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS season_scores (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  season_id BIGINT NOT NULL,
  uuid VARCHAR(64) NULL,
  name VARCHAR(64) NULL,
  score INT NOT NULL DEFAULT 0,
  wins INT NOT NULL DEFAULT 0,
  best_time_ms BIGINT NULL,
  lang_code VARCHAR(32) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY uniq_season_uuid (season_id, uuid),
  KEY idx_season_scores_score (score),
  KEY idx_season_scores_lang_code (lang_code),

  CONSTRAINT fk_season_scores_season
    FOREIGN KEY (season_id)
    REFERENCES seasons(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alltime_scores (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uuid VARCHAR(64) NULL,
  name VARCHAR(64) NULL,
  score INT NOT NULL DEFAULT 0,
  wins INT NOT NULL DEFAULT 0,
  best_time_ms BIGINT NULL,
  lang_code VARCHAR(32) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY uniq_alltime_uuid (uuid),
  KEY idx_alltime_scores_score (score),
  KEY idx_alltime_scores_lang_code (lang_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
