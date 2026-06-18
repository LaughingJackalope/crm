-- Transactional Outbox and Idempotency Log for the Support bounded context.

CREATE TABLE IF NOT EXISTS support.outbox_event (
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

    -- W3C trace context headers for distributed tracing across outbox boundary
    "metadata"        TEXT
);

CREATE INDEX IF NOT EXISTS ix_outbox_event_pending_support
    ON support.outbox_event (created_at ASC)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS ix_outbox_event_failed_support
    ON support.outbox_event (created_at ASC)
    WHERE status = 'FAILED';

CREATE TABLE IF NOT EXISTS support.incoming_event_log (
    event_id        UUID            NOT NULL PRIMARY KEY,
    event_type      VARCHAR(128)    NOT NULL,
    source          VARCHAR(32)     NOT NULL,
    entity_id       VARCHAR(36)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_incoming_event_log_type_support
    ON support.incoming_event_log (event_type, source);
