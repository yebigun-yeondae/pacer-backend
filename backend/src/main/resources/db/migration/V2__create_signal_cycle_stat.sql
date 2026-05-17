CREATE TABLE IF NOT EXISTS signal_cycle_stat (
    direction      VARCHAR(255) NOT NULL,
    itst_id        VARCHAR(255) NOT NULL,
    signal_type    VARCHAR(255) NOT NULL,
    state          VARCHAR(255) NOT NULL,
    avg_seconds    FLOAT8,
    calculated_at  TIMESTAMP,
    max_seconds    FLOAT8,
    min_seconds    FLOAT8,
    occurrences    BIGINT
);
