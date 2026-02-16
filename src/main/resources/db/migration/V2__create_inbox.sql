-- =============================================================================
-- V2: Inbox Table (Idempotency / Deduplication)
--
-- WHY INBOX PATTERN?
-- Kafka provides "at-least-once" delivery guarantee, meaning the same message
-- can be delivered multiple times (e.g., consumer crash before offset commit).
-- The inbox table acts as a deduplication barrier:
--   1. Before processing, check if idempotency_key exists in inbox.
--   2. If exists -> skip (already processed).
--   3. If not -> insert into inbox + process in the SAME transaction.
-- This transforms Kafka's at-least-once into effectively exactly-once processing.
--
-- WHY SHA-256 HASH as key?
-- Using SHA-256(event_name + user_id + timestamp + campaign_id) as the
-- idempotency key instead of a composite unique index because:
--   1. Single-column PK is faster for lookups than multi-column composite.
--   2. Key formula can be changed in application code without DB schema migration.
--   3. Fixed 64-char length regardless of input field lengths.
-- =============================================================================

CREATE TABLE inbox (
    idempotency_key VARCHAR(64)     PRIMARY KEY,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Index on received_at for the cleanup job that purges entries older than N days.
-- Without this index, the DELETE query would do a full table scan.
CREATE INDEX idx_inbox_received_at ON inbox (received_at);
