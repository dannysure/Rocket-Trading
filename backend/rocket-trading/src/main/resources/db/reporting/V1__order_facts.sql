-- BR-16: the reporting database. One row per order, copied from the trading database by
-- ReportingSnapshot. Analyst queries read only these tables, never the trading database.
-- No names, emails or credentials are copied: a client is an id and a risk profile segment.
CREATE TABLE order_facts (
    order_id            BIGINT PRIMARY KEY,
    client_id           BIGINT NOT NULL,
    risk_profile        TEXT,
    symbol              TEXT NOT NULL,
    asset_class         TEXT NOT NULL,
    market_segment      VARCHAR(10) NOT NULL,
    order_side          VARCHAR(4) NOT NULL,
    order_status        VARCHAR(20) NOT NULL,
    requested_quantity  NUMERIC(24, 8) NOT NULL,
    submitted_at        TIMESTAMPTZ NOT NULL,
    fill_id             BIGINT,
    executed_at         TIMESTAMPTZ,
    settled_notional    NUMERIC(24, 8)
);

CREATE INDEX idx_order_facts_submitted ON order_facts (submitted_at);
-- Orders still in flight are re-copied on every sync until they settle
CREATE INDEX idx_order_facts_open ON order_facts (order_status) WHERE order_status IN ('SUBMITTED', 'ACCEPTED');

-- How far through the trading audit trail the copy has got
CREATE TABLE sync_state (
    id                  SMALLINT PRIMARY KEY CHECK (id = 1),
    last_audit_id       BIGINT NOT NULL,
    synced_at           TIMESTAMPTZ
);
INSERT INTO sync_state (id, last_audit_id, synced_at) VALUES (1, 0, NULL);
