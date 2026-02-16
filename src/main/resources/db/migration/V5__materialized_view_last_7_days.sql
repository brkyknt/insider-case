-- =============================================================================
-- V5: Materialized View — Son N günle sınırla (parametrik, partition pruning)
--
-- Neden: REFRESH sırasında tüm partition'ları taramak gün geçtikçe yavaşlar.
-- Son N günlük veriyle sınırlayarak sadece ilgili partition'lar taranır;
-- refresh süresi kabaca sabit kalır. N, app_config tablosundan okunur.
--
-- Değiştirmek: UPDATE app_config SET value = '14' WHERE key = 'mv_retention_days';
--             Sonraki REFRESH yeni değeri kullanır. Varsayılan: 7.
-- =============================================================================

-- MATERIALIZED VIEW penceresi (gün sayısı) — uygulama application.yaml ile senkronize edebilir
CREATE TABLE IF NOT EXISTS app_config (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
INSERT INTO app_config (key, value) VALUES ('mv_retention_days', '7')
ON CONFLICT (key) DO NOTHING;

DROP MATERIALIZED VIEW IF EXISTS event_metrics CASCADE;

CREATE MATERIALIZED VIEW event_metrics AS
SELECT
    event_name,
    channel,
    DATE_TRUNC('hour', TO_TIMESTAMP(event_timestamp)) AS date_hour,
    event_date,
    COUNT(*)                    AS total_count,
    COUNT(DISTINCT user_id)     AS unique_user_count
FROM events
WHERE event_date >= CURRENT_DATE - COALESCE(
    (SELECT NULLIF(TRIM(value), '')::int FROM app_config WHERE key = 'mv_retention_days'),
    7
) * INTERVAL '1 day'
GROUP BY event_name, channel, DATE_TRUNC('hour', TO_TIMESTAMP(event_timestamp)), event_date;

-- REFRESH MATERIALIZED VIEW CONCURRENTLY için zorunlu
CREATE UNIQUE INDEX idx_event_metrics_unique
    ON event_metrics (event_name, channel, date_hour, event_date);

-- Metrik API range sorguları için (V4'teki index)
CREATE INDEX idx_event_metrics_date_range
    ON event_metrics (event_name, date_hour);
