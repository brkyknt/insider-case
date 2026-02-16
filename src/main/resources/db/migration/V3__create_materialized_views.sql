-- =============================================================================
-- V4: Materialized View for Metrics Aggregation
--
-- WHY MATERIALIZED VIEW?
-- The case states: "Metrics endpoint does not need to be fully real-time."
-- At ~172M events/day, running COUNT/COUNT DISTINCT on the raw events table
-- for every metrics API call would be prohibitively expensive.
--
-- A materialized view pre-computes aggregations:
--   - Total event count per (event_name, channel, hour)
--   - Unique user count per (event_name, channel, hour)
-- Queries on the materialized view are orders of magnitude faster.
--
-- REFRESH CONCURRENTLY allows the view to be refreshed without locking reads,
-- meaning the metrics API always returns data (possibly slightly stale, which
-- is acceptable per requirements).
--
-- Refresh frequency: every 1-5 minutes via a Spring @Scheduled job.
-- =============================================================================

CREATE MATERIALIZED VIEW event_metrics AS
SELECT
    event_name,
    channel,
    DATE_TRUNC('hour', TO_TIMESTAMP(event_timestamp)) AS date_hour,
    event_date,
    COUNT(*)                    AS total_count,
    COUNT(DISTINCT user_id)     AS unique_user_count
FROM events
GROUP BY event_name, channel, DATE_TRUNC('hour', TO_TIMESTAMP(event_timestamp)), event_date;

-- UNIQUE index is REQUIRED for REFRESH MATERIALIZED VIEW CONCURRENTLY.
-- Without it, PostgreSQL cannot do a concurrent (non-locking) refresh.
CREATE UNIQUE INDEX idx_event_metrics_unique
    ON event_metrics (event_name, channel, date_hour, event_date);
