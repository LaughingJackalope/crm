CREATE TABLE IF NOT EXISTS communication.notification (
    notification_id     UUID            NOT NULL PRIMARY KEY,
    recipient_id        VARCHAR(36)     NOT NULL,
    channel_type        VARCHAR(16)     NOT NULL,
    template_id         VARCHAR(64),
    subject             VARCHAR(500),
    body                TEXT            NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED')),
    retry_count         INTEGER         NOT NULL DEFAULT 0,
    max_retries         INTEGER         NOT NULL DEFAULT 3,
    provider_message_id VARCHAR(128),
    failure_reason      VARCHAR(500),
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_notification_status ON communication.notification (status);
CREATE INDEX IF NOT EXISTS ix_notification_recipient ON communication.notification (recipient_id);

CREATE TABLE IF NOT EXISTS communication.notification_template_vars (
    notification_id     UUID            NOT NULL REFERENCES communication.notification(notification_id) ON DELETE CASCADE,
    var_key             VARCHAR(64)     NOT NULL,
    var_value           VARCHAR(500)    NOT NULL,
    PRIMARY KEY (notification_id, var_key)
);
