CREATE TABLE perpetual_markets (
    market_id                BIGSERIAL PRIMARY KEY,
    market_symbol            TEXT NOT NULL UNIQUE,
    display_name             TEXT NOT NULL,
    asset_symbol             TEXT NOT NULL,
    quote_currency           VARCHAR(3) NOT NULL,
    settlement_asset         VARCHAR(10) NOT NULL,
    spot_symbol              TEXT NOT NULL,
    trading_view_symbol      TEXT NOT NULL,
    contract_size            NUMERIC(18,8) NOT NULL CHECK (contract_size > 0),
    annual_interest_rate     NUMERIC(10,8) NOT NULL CHECK (annual_interest_rate >= 0),
    funding_interval_hours   INTEGER NOT NULL CHECK (funding_interval_hours > 0),
    funding_clamp_bps        NUMERIC(10,4) NOT NULL CHECK (funding_clamp_bps >= 0),
    maker_fee_rate           NUMERIC(10,8) NOT NULL CHECK (maker_fee_rate >= 0),
    taker_fee_rate           NUMERIC(10,8) NOT NULL CHECK (taker_fee_rate >= 0),
    maintenance_margin_rate  NUMERIC(10,8) NOT NULL CHECK (maintenance_margin_rate > 0),
    max_leverage             INTEGER NOT NULL CHECK (max_leverage > 0),
    impact_notional_usd      NUMERIC(18,2) NOT NULL CHECK (impact_notional_usd > 0),
    insurance_fund_usd       NUMERIC(18,2) NOT NULL CHECK (insurance_fund_usd >= 0),
    base_basis_bps           NUMERIC(10,4) NOT NULL DEFAULT 0,
    is_active                BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE perpetual_positions (
    position_id              BIGSERIAL PRIMARY KEY,
    client_id                BIGINT NOT NULL REFERENCES client_profiles(client_id),
    account_id               BIGINT NOT NULL REFERENCES client_accounts(account_id),
    market_id                BIGINT NOT NULL REFERENCES perpetual_markets(market_id),
    signed_quantity          NUMERIC(18,6) NOT NULL DEFAULT 0,
    entry_price              NUMERIC(16,4) NOT NULL DEFAULT 0,
    leverage                 INTEGER NOT NULL CHECK (leverage > 0),
    realized_pnl_usd         NUMERIC(18,4) NOT NULL DEFAULT 0,
    cumulative_funding_usd   NUMERIC(18,4) NOT NULL DEFAULT 0,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_perpetual_position UNIQUE (account_id, market_id)
);

CREATE TABLE perpetual_orders (
    order_id                 BIGSERIAL PRIMARY KEY,
    client_id                BIGINT NOT NULL REFERENCES client_profiles(client_id),
    account_id               BIGINT NOT NULL REFERENCES client_accounts(account_id),
    market_id                BIGINT NOT NULL REFERENCES perpetual_markets(market_id),
    order_side               VARCHAR(4) NOT NULL CHECK (order_side IN ('BUY', 'SELL')),
    order_type               VARCHAR(10) NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT')),
    reduce_only              BOOLEAN NOT NULL DEFAULT FALSE,
    quantity                 NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    leverage                 INTEGER NOT NULL CHECK (leverage > 0),
    limit_price              NUMERIC(16,4),
    status                   VARCHAR(15) NOT NULL CHECK (status IN ('SUBMITTED', 'FILLED', 'REJECTED')),
    rejection_reason         TEXT,
    mark_price               NUMERIC(16,4) NOT NULL CHECK (mark_price > 0),
    average_fill_price       NUMERIC(16,4),
    notional_usd             NUMERIC(18,4) NOT NULL CHECK (notional_usd >= 0),
    fee_usd                  NUMERIC(18,4) NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    filled_at                TIMESTAMPTZ
);

CREATE TABLE perpetual_fills (
    fill_id                  BIGSERIAL PRIMARY KEY,
    order_id                 BIGINT NOT NULL REFERENCES perpetual_orders(order_id) ON DELETE CASCADE,
    quantity                 NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    price                    NUMERIC(16,4) NOT NULL CHECK (price > 0),
    fee_usd                  NUMERIC(18,4) NOT NULL DEFAULT 0,
    realized_pnl_usd         NUMERIC(18,4) NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_perpetual_orders_market_account ON perpetual_orders(market_id, account_id, created_at DESC);
CREATE INDEX idx_perpetual_positions_market ON perpetual_positions(market_id);
CREATE INDEX idx_perpetual_fills_order ON perpetual_fills(order_id, created_at DESC);

INSERT INTO perpetual_markets (
    market_symbol,
    display_name,
    asset_symbol,
    quote_currency,
    settlement_asset,
    spot_symbol,
    trading_view_symbol,
    contract_size,
    annual_interest_rate,
    funding_interval_hours,
    funding_clamp_bps,
    maker_fee_rate,
    taker_fee_rate,
    maintenance_margin_rate,
    max_leverage,
    impact_notional_usd,
    insurance_fund_usd,
    base_basis_bps,
    is_active
)
VALUES (
    'ETH-PERP',
    'Ethereum Perpetual Market',
    'ETH',
    'USD',
    'USDC',
    'ETH-USD',
    'BITSTAMP:ETHUSD',
    1.00000000,
    0.05000000,
    8,
    5.0000,
    0.00020000,
    0.00055000,
    0.00500000,
    50,
    250000.00,
    38500000.00,
    2.0000,
    TRUE
)
ON CONFLICT (market_symbol) DO NOTHING;
