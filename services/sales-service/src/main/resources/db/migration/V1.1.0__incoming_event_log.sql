-- Idempotency log for incoming events from upstream Bounded Contexts.
-- Used by the CustomerEventConsumer to implement the Idempotent Consumer pattern.
--
-- Before processing any Kafka event, the consumer checks this table for
-- the event's idempotency key. If found, the event is a duplicate and is
-- acknowledged without re-processing.
--
-- After successful processing, the key is recorded here within the same
-- transaction as the domain changes (opportunity creation).

CREATE TABLE IF NOT EXISTS sales.incoming_event_log (
    event_id        UUID            NOT NULL PRIMARY KEY,
    event_type      VARCHAR(128)    NOT NULL,
    source          VARCHAR(32)     NOT NULL,
    entity_id       VARCHAR(36)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Index for monitoring: count of processed events by type.
CREATE INDEX IF NOT EXISTS ix_incoming_event_log_type
    ON sales.incoming_event_log (event_type, source);

-- Index for cleanup: find old records.
CREATE INDEX IF NOT EXISTS ix_incoming_event_log_processed_at
    ON sales.incoming_event_log (processed_at);
