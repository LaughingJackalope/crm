-- Transactional Outbox table for the CIAM bounded context.
-- Stores domain events durably alongside aggregate state, then a background
-- relay polls this table and publishes to Kafka.
--
-- This guarantees atomicity: either both the aggregate update AND the event
-- outbox row commit together, or neither does.

CREATE TABLE IF NOT EXISTS ciam.outbox_event (
    event_id        UUID            NOT NULL PRIMARY KEY,
    entity_id       VARCHAR(36)     NOT NULL,
    entity_type     VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    source          VARCHAR(32)     NOT NULL,
    payload         TEXT            NOT NULL,
    correlation_id  VARCHAR(36),
    actor_id        VARCHAR(128),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count     INTEGER         NOT NULL DEFAULT 0
);

-- Index for the relay's polling query: pending events oldest-first.
CREATE INDEX IF NOT EXISTS ix_outbox_event_pending
    ON ciam.outbox_event (created_at ASC)
    WHERE status = 'PENDING';

-- Index for retry queries.
CREATE INDEX IF NOT EXISTS ix_outbox_event_failed
    ON ciam.outbox_event (created_at ASC)
    WHERE status = 'FAILED';

-- Index for monitoring: count by status.
CREATE INDEX IF NOT EXISTS ix_outbox_event_status
    ON ciam.outbox_event (status);
