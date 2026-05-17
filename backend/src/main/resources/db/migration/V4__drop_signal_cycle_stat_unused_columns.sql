ALTER TABLE signal_cycle_stat
    DROP COLUMN IF EXISTS avg_seconds,
    DROP COLUMN IF EXISTS min_seconds,
    DROP COLUMN IF EXISTS occurrences;

ALTER TABLE signal_cycle_stat
    RENAME COLUMN max_seconds TO cycle_seconds;
