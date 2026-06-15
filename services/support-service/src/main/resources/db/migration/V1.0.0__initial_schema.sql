-- Initial schema for the Support bounded context.

CREATE TABLE IF NOT EXISTS support.ticket (
    ticket_id       UUID            NOT NULL PRIMARY KEY,
    requester_id    VARCHAR(36)     NOT NULL,
    assignee_id     VARCHAR(36),
    subject         VARCHAR(500)    NOT NULL,
    description     TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'PENDING_CUSTOMER', 'RESOLVED', 'CLOSED')),
    priority        VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM'
                    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    queue_id        VARCHAR(36),
    sla_deadline    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    resolved_at     TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_ticket_requester_id ON support.ticket (requester_id);
CREATE INDEX IF NOT EXISTS ix_ticket_assignee_id ON support.ticket (assignee_id);
CREATE INDEX IF NOT EXISTS ix_ticket_status ON support.ticket (status);
CREATE INDEX IF NOT EXISTS ix_ticket_sla_deadline ON support.ticket (sla_deadline)
    WHERE sla_deadline IS NOT NULL AND status NOT IN ('CLOSED', 'RESOLVED');

CREATE TABLE IF NOT EXISTS support.customer_tier_projection (
    contact_id      VARCHAR(36)     NOT NULL PRIMARY KEY,
    customer_id     VARCHAR(36)     NOT NULL,
    tier            VARCHAR(16)     NOT NULL DEFAULT 'STANDARD'
                    CHECK (tier IN ('STANDARD', 'PREMIUM', 'ENTERPRISE')),
    lifecycle_stage VARCHAR(16)     NOT NULL DEFAULT 'lead',
    updated_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_tier_projection_customer_id ON support.customer_tier_projection (customer_id);
CREATE INDEX IF NOT EXISTS ix_tier_projection_tier ON support.customer_tier_projection (tier);
