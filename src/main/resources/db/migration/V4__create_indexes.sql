-- =============================================================================
-- V5: Indexes for Query Performance
--
-- Index strategy is driven by the two main query patterns:
--   1. Idempotency check: WHERE idempotency_key = ? (covered by inbox PK)
--   2. Metrics queries: WHERE event_name = ? AND event_date BETWEEN ? AND ?
--      (covered by materialized view + the index below for raw table fallback)
-- =============================================================================

-- Composite index for metrics queries that fall through to the raw events table.
-- event_name is the mandatory filter, event_date enables partition pruning,
-- user_id supports COUNT(DISTINCT user_id) without a full scan.
CREATE INDEX idx_events_name_date ON events (event_name, event_date);

-- Idempotency key index on events table for fast duplicate lookups during batch inserts.
-- This is separate from the inbox table check — it's a safety net at the DB level.
CREATE UNIQUE INDEX idx_events_idempotency ON events (idempotency_key, event_date);

-- Channel index for metrics filtering by channel
CREATE INDEX idx_events_channel ON events (channel, event_date);

-- Materialized view indexes for the metrics API query patterns.
-- The unique index (from V4) covers exact matches.
-- This additional index supports range queries on date_hour.
CREATE INDEX idx_event_metrics_date_range
    ON event_metrics (event_name, date_hour);
