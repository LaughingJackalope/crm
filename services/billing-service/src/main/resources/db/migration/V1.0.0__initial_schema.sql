-- Initial schema for the Billing bounded context.

CREATE TABLE IF NOT EXISTS billing.invoice (
    invoice_id      UUID            NOT NULL PRIMARY KEY,
    opportunity_id  VARCHAR(36)     NOT NULL UNIQUE,
    customer_id     VARCHAR(36)     NOT NULL,
    account_id      VARCHAR(36),
    invoice_number  VARCHAR(64)     NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'OVERDUE', 'VOID')),
    issue_date      DATE            NOT NULL,
    due_date        DATE            NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    subtotal        DECIMAL(19,2)   NOT NULL DEFAULT 0,
    total_tax       DECIMAL(19,2)   NOT NULL DEFAULT 0,
    total           DECIMAL(19,2)   NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_invoice_customer_id ON billing.invoice (customer_id);
CREATE INDEX IF NOT EXISTS ix_invoice_status ON billing.invoice (status);
CREATE INDEX IF NOT EXISTS ix_invoice_due_date ON billing.invoice (due_date);

CREATE TABLE IF NOT EXISTS billing.invoice_line_item (
    line_item_id    UUID            NOT NULL PRIMARY KEY,
    invoice_id      UUID            NOT NULL REFERENCES billing.invoice(invoice_id) ON DELETE CASCADE,
    description     VARCHAR(500)    NOT NULL,
    quantity        DECIMAL(19,4)   NOT NULL,
    unit_price      DECIMAL(19,4)   NOT NULL,
    tax_rate        DECIMAL(5,2)    NOT NULL DEFAULT 0,
    line_total      DECIMAL(19,2)   NOT NULL,
    tax_amount      DECIMAL(19,2)   NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_line_item_invoice_id ON billing.invoice_line_item (invoice_id);
