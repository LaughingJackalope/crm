-- Initial schema for the Sales bounded context.

CREATE TABLE IF NOT EXISTS sales.opportunity (
    opportunity_id      UUID            NOT NULL PRIMARY KEY,
    customer_id         VARCHAR(36)     NOT NULL,
    account_id          VARCHAR(36),
    name                VARCHAR(255)    NOT NULL,
    stage               VARCHAR(20)     NOT NULL DEFAULT 'PROSPECTING',
    amount_value        DECIMAL(19,4)   NOT NULL DEFAULT 0,
    amount_currency     VARCHAR(3)      NOT NULL DEFAULT 'USD',
    probability         INTEGER         NOT NULL DEFAULT 0,
    expected_close_date DATE,
    owner_id            VARCHAR(36),
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_opportunity_customer_id
    ON sales.opportunity (customer_id);

CREATE INDEX IF NOT EXISTS ix_opportunity_stage
    ON sales.opportunity (stage);
