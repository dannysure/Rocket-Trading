-- Perpetual futures with isolated margin. Collateral comes from the DIRECT_TRADING cash balance;
-- spot tables (orders, fills, transactions, holdings) are not touched by perp trading.
CREATE TABLE perp_markets (
    market_id           BIGSERIAL PRIMARY KEY,
    market_symbol       VARCHAR(20) NOT NULL UNIQUE,
    base_asset          VARCHAR(10) NOT NULL,
    underlying_symbol   TEXT NOT NULL,
    underlying_market   VARCHAR(10) NOT NULL CHECK (underlying_market IN ('stock', 'crypto')),
    quote_currency      VARCHAR(3) NOT NULL,
    max_leverage        INTEGER NOT NULL CHECK (max_leverage BETWEEN 1 AND 100),
    taker_fee_rate      NUMERIC(8,6) NOT NULL CHECK (taker_fee_rate >= 0),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO perp_markets (market_symbol, base_asset, underlying_symbol, underlying_market, quote_currency, max_leverage, taker_fee_rate)
VALUES
    ('BTC-PERP', 'BTC', 'X:BTC-USD', 'crypto', 'USD', 40, 0.000450),
    ('ETH-PERP', 'ETH', 'X:ETH-USD', 'crypto', 'USD', 25, 0.000450);

CREATE TABLE perp_positions (
    position_id         BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client_profiles(client_id),
    account_id          BIGINT NOT NULL REFERENCES client_accounts(account_id),
    market_id           BIGINT NOT NULL REFERENCES perp_markets(market_id),
    position_side       VARCHAR(5) NOT NULL CHECK (position_side IN ('LONG', 'SHORT')),
    size                NUMERIC(18,6) NOT NULL CHECK (size > 0),
    entry_price         NUMERIC(20,8) NOT NULL CHECK (entry_price > 0),
    isolated_margin     NUMERIC(16,4) NOT NULL,
    leverage            INTEGER NOT NULL CHECK (leverage >= 1),
    position_status     VARCHAR(10) NOT NULL CHECK (position_status IN ('OPEN', 'CLOSED', 'LIQUIDATED')),
    realized_pnl        NUMERIC(16,4) NOT NULL DEFAULT 0,
    cumulative_funding  NUMERIC(16,4) NOT NULL DEFAULT 0,
    opened_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at           TIMESTAMPTZ
);
-- One net position per market per account
CREATE UNIQUE INDEX uq_open_perp_position ON perp_positions(account_id, market_id) WHERE position_status = 'OPEN';
CREATE INDEX idx_open_perp_positions ON perp_positions(position_id) WHERE position_status = 'OPEN';

-- Market orders fill or reject synchronously, so each row is written once in its final state.
CREATE TABLE perp_orders (
    perp_order_id       BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client_profiles(client_id),
    account_id          BIGINT NOT NULL REFERENCES client_accounts(account_id),
    market_id           BIGINT NOT NULL REFERENCES perp_markets(market_id),
    position_id         BIGINT REFERENCES perp_positions(position_id),
    order_side          VARCHAR(4) NOT NULL CHECK (order_side IN ('BUY', 'SELL')),
    order_kind          VARCHAR(12) NOT NULL CHECK (order_kind IN ('MARKET', 'LIQUIDATION')),
    requested_size      NUMERIC(18,6) NOT NULL CHECK (requested_size > 0),
    filled_size         NUMERIC(18,6),
    leverage            INTEGER NOT NULL,
    reduce_only         BOOLEAN NOT NULL DEFAULT FALSE,
    order_status        VARCHAR(10) NOT NULL CHECK (order_status IN ('FILLED', 'REJECTED')),
    fill_price          NUMERIC(20,8),
    fee                 NUMERIC(16,4) NOT NULL DEFAULT 0,
    realized_pnl        NUMERIC(16,4) NOT NULL DEFAULT 0,
    rejection_reason    TEXT,
    idempotency_key     VARCHAR(100),
    request_fingerprint TEXT,
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_perp_client_request UNIQUE (client_id, idempotency_key)
);
CREATE INDEX idx_perp_orders_client ON perp_orders(client_id, submitted_at DESC);

CREATE TABLE perp_funding_payments (
    funding_id          BIGSERIAL PRIMARY KEY,
    position_id         BIGINT NOT NULL REFERENCES perp_positions(position_id),
    client_id           BIGINT NOT NULL REFERENCES client_profiles(client_id),
    market_id           BIGINT NOT NULL REFERENCES perp_markets(market_id),
    funding_time        TIMESTAMPTZ NOT NULL,
    position_side       VARCHAR(5) NOT NULL CHECK (position_side IN ('LONG', 'SHORT')),
    size                NUMERIC(18,6) NOT NULL,
    mark_price          NUMERIC(20,8) NOT NULL,
    funding_rate        NUMERIC(14,10) NOT NULL,
    payment             NUMERIC(16,4) NOT NULL,
    -- A position is charged at most once per funding interval, even across restarts or multiple workers
    CONSTRAINT uq_position_funding_time UNIQUE (position_id, funding_time)
);
CREATE INDEX idx_perp_funding_client ON perp_funding_payments(client_id, funding_time DESC);

CREATE TRIGGER protect_perp_orders BEFORE UPDATE OR DELETE ON perp_orders FOR EACH ROW EXECUTE FUNCTION prevent_record_changes();
CREATE TRIGGER protect_perp_funding BEFORE UPDATE OR DELETE ON perp_funding_payments FOR EACH ROW EXECUTE FUNCTION prevent_record_changes();
