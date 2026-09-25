DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS fills CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS market_quotes CASCADE;
DROP TABLE IF EXISTS client_holdings CASCADE;
DROP TABLE IF EXISTS account_holdings CASCADE;
DROP TABLE IF EXISTS client_accounts CASCADE;
DROP TABLE IF EXISTS financial_instruments CASCADE;
DROP TABLE IF EXISTS client_sessions CASCADE;
DROP TABLE IF EXISTS client_profiles CASCADE;
DROP TABLE IF EXISTS financial_advisors CASCADE;

CREATE TABLE financial_advisors (
    advisor_id          SERIAL PRIMARY KEY,
    advisor_full_name   TEXT NOT NULL,
    regional_office     TEXT NOT NULL,
    hired_date          DATE NOT NULL
);

CREATE TABLE client_profiles (
    client_id           BIGINT PRIMARY KEY,
    client_full_name    TEXT NOT NULL,
    email_address       TEXT NOT NULL UNIQUE,
    date_of_birth       DATE NOT NULL,
    risk_profile        TEXT NOT NULL CHECK (risk_profile IN ('Cautious', 'Balanced', 'Adventurous')),
    advisor_id          BIGINT REFERENCES financial_advisors(advisor_id),
    registered_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE client_sessions (
    session_id          BIGINT PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client_profiles(client_id),
    expires_at          TIMESTAMP NOT NULL
);

CREATE TABLE financial_instruments (
    instrument_id       BIGSERIAL PRIMARY KEY,
    ticker_symbol       TEXT NOT NULL UNIQUE,
    instrument_name     TEXT NOT NULL,
    asset_class         TEXT NOT NULL CHECK (asset_class IN ('Equity', 'Bond', 'Fund', 'FX', 'Crypto', 'Cash')),
    base_currency       VARCHAR(3) NOT NULL,
    is_tradable         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE client_accounts (
    account_id          BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client_profiles(client_id),
    account_type        TEXT NOT NULL CHECK (account_type IN ('ISA', 'GIA', 'SIPP', 'DIRECT_TRADING')),
    cash_balance        NUMERIC(16,4) NOT NULL DEFAULT 0.0000 CHECK (cash_balance >= 0),
    currency            VARCHAR(3) NOT NULL,
    opened_date         DATE NOT NULL
);

CREATE TABLE account_holdings (
    holding_id          BIGSERIAL PRIMARY KEY,
    account_id          BIGINT NOT NULL REFERENCES client_accounts(account_id),
    instrument_id       BIGINT NOT NULL REFERENCES financial_instruments(instrument_id),
    quantity            NUMERIC(18,6) NOT NULL CHECK (quantity >= 0),
    as_of_timestamp     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_account_instrument UNIQUE (account_id, instrument_id)
);

CREATE TABLE client_holdings (
    client_holding_id   BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client_profiles(client_id),
    instrument_id       BIGINT NOT NULL REFERENCES financial_instruments(instrument_id),
    total_quantity      NUMERIC(18,6) NOT NULL CHECK (total_quantity >= 0),
    as_of_timestamp     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_client_instrument UNIQUE (client_id, instrument_id)
);

CREATE TABLE market_quotes (
    quote_id            BIGSERIAL PRIMARY KEY,
    instrument_id       BIGINT NOT NULL REFERENCES financial_instruments(instrument_id),
    bid_price           NUMERIC(16,4) NOT NULL CHECK (bid_price > 0),
    ask_price           NUMERIC(16,4) NOT NULL CHECK (ask_price > 0),
    quote_timestamp     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    order_id            BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client_profiles(client_id),
    account_id          BIGINT NOT NULL REFERENCES client_accounts(account_id),
    instrument_id       BIGINT NOT NULL REFERENCES financial_instruments(instrument_id),
    order_side          VARCHAR(4) NOT NULL CHECK (order_side IN ('BUY', 'SELL')),
    order_type          VARCHAR(10) NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT')),
    requested_quantity  NUMERIC(18,6) NOT NULL CHECK (requested_quantity > 0),
    limit_price         NUMERIC(16,4) CHECK (limit_price > 0),
    order_status        VARCHAR(15) NOT NULL DEFAULT 'SUBMITTED'
                        CHECK (order_status IN ('SUBMITTED', 'ACCEPTED', 'FILLED', 'REJECTED', 'CANCELLED')),
    rejection_reason    TEXT,
    submitted_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fills (
    fill_id             BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(order_id),
    executed_quantity   NUMERIC(18,6) NOT NULL CHECK (executed_quantity > 0),
    executed_price      NUMERIC(16,4) NOT NULL CHECK (executed_price > 0),
    quote_id            BIGINT REFERENCES market_quotes(quote_id),
    executed_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transactions (
    transaction_id      BIGSERIAL PRIMARY KEY,
    account_id          BIGINT NOT NULL REFERENCES client_accounts(account_id),
    instrument_id       BIGINT REFERENCES financial_instruments(instrument_id),
    fill_id             BIGINT REFERENCES fills(fill_id),
    transaction_type    VARCHAR(15) NOT NULL CHECK (transaction_type IN ('BUY', 'SELL', 'DIVIDEND', 'DEPOSIT', 'WITHDRAWAL')),
    quantity            NUMERIC(18,6),
    unit_price          NUMERIC(16,4),
    net_amount          NUMERIC(16,4) NOT NULL,
    transaction_date    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    audit_id            BIGSERIAL PRIMARY KEY,
    entity_name         VARCHAR(50) NOT NULL,
    entity_id           BIGINT NOT NULL,
    action_type         VARCHAR(10) NOT NULL CHECK (action_type IN ('INSERT', 'UPDATE', 'DELETE')),
    client_id           BIGINT REFERENCES client_profiles(client_id),
    state_before        TEXT,
    state_after         TEXT,
    recorded_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_client_id ON orders(client_id);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_fills_order_id ON fills(order_id);
CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_market_quotes_lookup ON market_quotes(instrument_id, quote_timestamp DESC);
CREATE INDEX idx_audit_lookup ON audit_logs(entity_name, entity_id);
