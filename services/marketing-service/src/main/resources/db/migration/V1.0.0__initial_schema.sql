CREATE TABLE IF NOT EXISTS marketing.campaign (
    campaign_id         UUID            NOT NULL PRIMARY KEY,
    name                VARCHAR(255)    NOT NULL,
    source              VARCHAR(16)     NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    target_segment      VARCHAR(255)    NOT NULL,
    start_date          DATE,
    end_date            DATE,
    budget              DECIMAL(19,2)   NOT NULL DEFAULT 0,
    impressions         BIGINT          NOT NULL DEFAULT 0,
    clicks              BIGINT          NOT NULL DEFAULT 0,
    spend               DECIMAL(19,2)   NOT NULL DEFAULT 0,
    attributed_revenue  DECIMAL(19,2)   NOT NULL DEFAULT 0,
    attributed_conversions BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_campaign_status ON marketing.campaign (status);
CREATE INDEX IF NOT EXISTS ix_campaign_source ON marketing.campaign (source);

CREATE TABLE IF NOT EXISTS marketing.campaign_attributed_opportunities (
    campaign_id     UUID            NOT NULL REFERENCES marketing.campaign(campaign_id) ON DELETE CASCADE,
    opportunity_id  VARCHAR(36)     NOT NULL,
    PRIMARY KEY (campaign_id, opportunity_id)
);
