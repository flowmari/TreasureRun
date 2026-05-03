-- Purpose:
--   - Officially support MONTHLY seasons in addition to WEEKLY
--   - Relax the seasons uniqueness so monthly rows can coexist cleanly
--   - Keep migration idempotent enough for already-updated local DBs

-- NOTE:
--   V1 is already applied in schema_migrations.
--   Do NOT rewrite V1 for existing environments.
--   Add V2 instead.

ALTER TABLE seasons
DROP INDEX uniq_season_type_year_week,
ADD UNIQUE KEY uniq_season_type_year_week (season_type, year, week, season_key);
