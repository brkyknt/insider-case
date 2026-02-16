-- =============================================================================
-- V1: Events Table (Range Partitioned by Day)
--
-- WHY PARTITIONING?
-- At ~2,000 events/sec average, this table grows by ~172M rows/day.
-- Range partitioning by event_date enables:
--   1. Partition pruning: Metrics queries with time-range filters only scan
--      relevant partitions, not the entire table.
--   2. Efficient retention: Old data can be dropped via DROP TABLE on the
--      partition, which is O(1) instead of DELETE which is O(n).
--   3. Parallel query execution across partitions.
--
-- WHY JSONB for tags/metadata?
-- The event payload has variable-structure fields (tags array, metadata object).
-- JSONB provides flexible schema while still supporting GIN indexes for queries.
-- This avoids a rigid relational schema for fields that vary by event type.
-- =============================================================================

CREATE TABLE events (
    id              BIGSERIAL       NOT NULL,
    event_name      VARCHAR(255)    NOT NULL,
    channel         VARCHAR(100),
    campaign_id     VARCHAR(100),
    user_id         VARCHAR(255)    NOT NULL,
    event_timestamp BIGINT          NOT NULL,
    event_date      DATE            NOT NULL,
    tags            JSONB,
    metadata        JSONB,
    idempotency_key VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, event_date)
) PARTITION BY RANGE (event_date);

-- Create partitions for the current month and next month.
-- In production, a scheduled job or pg_partman extension would auto-create partitions.
-- For the assessment scope, we pre-create a reasonable range.
DO $$
DECLARE
    start_date DATE := DATE_TRUNC('month', CURRENT_DATE);
    end_date   DATE := start_date + INTERVAL '2 months';
    d          DATE := start_date;
BEGIN
    WHILE d < end_date LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS events_%s PARTITION OF events FOR VALUES FROM (%L) TO (%L)',
            TO_CHAR(d, 'YYYY_MM_DD'),
            d,
            d + INTERVAL '1 day'
        );
        d := d + INTERVAL '1 day';
    END LOOP;
END $$;
