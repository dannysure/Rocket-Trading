DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS fills CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS market_quotes CASCADE;
DROP TABLE IF EXISTS client_subscriptions CASCADE;
DROP TABLE IF EXISTS model_portfolio_holdings CASCADE;
DROP TABLE IF EXISTS model_portfolios CASCADE;
DROP TABLE IF EXISTS client_holdings CASCADE;
DROP TABLE IF EXISTS account_holdings CASCADE;
DROP TABLE IF EXISTS client_accounts CASCADE;
DROP TABLE IF EXISTS financial_instruments CASCADE;
DROP TABLE IF EXISTS client_profiles CASCADE;
DROP TABLE IF EXISTS financial_advisors CASCADE;

-- -----------------------------------------------------------------------------
-- 1. FINANCIAL ADVISORS
-- BRS Coverage: Context / Section 2 (Advised Channels Transition)
-- Description: Stores details of legacy phone desk advisors and relationship 
--              managers for historical continuity.
-- -----------------------------------------------------------------------------
CREATE TABLE financial_advisors (
    advisor_id          SERIAL PRIMARY KEY,
    advisor_full_name   TEXT NOT NULL,
    regional_office     TEXT NOT NULL,
    hired_date          DATE NOT NULL
);

-- -----------------------------------------------------------------------------
-- 2. CLIENT PROFILES
-- BRS Coverage: BR-01 (Registration/Sign-in), BR-02 (Data Isolation)
-- Description: Central investor profile store. Enforces unique client identity 
--              and serves as the anchor for row-level security.
-- -----------------------------------------------------------------------------
CREATE TABLE client_profiles (
    client_id           SERIAL PRIMARY KEY,
    client_full_name    TEXT NOT NULL,
    date_of_birth       DATE NOT NULL,
    risk_profile        TEXT NOT NULL CHECK (risk_profile IN ('Cautious', 'Balanced', 'Adventurous')),
    advisor_id          INTEGER REFERENCES financial_advisors(advisor_id),
    registered_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 3. FINANCIAL INSTRUMENTS
-- BRS Coverage: BR-12 (Multi-Asset: Equities UK/US/IN, FX, Crypto)
-- Description: Master instrument directory storing tradable assets, ticker 
--              symbols, asset class categories, and base currencies.
-- -----------------------------------------------------------------------------
CREATE TABLE financial_instruments (
    instrument_id       SERIAL PRIMARY KEY,
    ticker_symbol       TEXT NOT NULL UNIQUE,
    instrument_name     TEXT NOT NULL,
    asset_class         TEXT NOT NULL CHECK (asset_class IN ('Equity', 'Bond', 'Fund', 'FX', 'Crypto', 'Cash')),
    base_currency       VARCHAR(3) NOT NULL,
    is_tradable         BOOLEAN NOT NULL DEFAULT TRUE
);

-- -----------------------------------------------------------------------------
-- 4. CLIENT ACCOUNTS
-- BRS Coverage: BR-02 (Data Isolation), BR-10 (Holdings & Cash View)
-- Description: Represents individual wrappers (ISA, GIA, SIPP, Direct Trading) 
--              and holds real-time available cash balances.
-- -----------------------------------------------------------------------------
CREATE TABLE client_accounts (
    account_id          SERIAL PRIMARY KEY,
    client_id           INTEGER NOT NULL REFERENCES client_profiles(client_id),
    account_type        TEXT NOT NULL CHECK (account_type IN ('ISA', 'GIA', 'SIPP', 'DIRECT_TRADING')),
    cash_balance        NUMERIC(16,4) NOT NULL DEFAULT 0.0000 CHECK (cash_balance >= 0),
    currency            VARCHAR(3) NOT NULL,
    opened_date         DATE NOT NULL
);

-- -----------------------------------------------------------------------------
-- 5. ACCOUNT HOLDINGS
-- BRS Coverage: BR-10 (Real-time Holdings View)
-- Description: Stores current quantity of specific instruments held inside an 
--              individual account wrapper. Updated atomically on trade execution.
-- -----------------------------------------------------------------------------
CREATE TABLE account_holdings (
    holding_id          SERIAL PRIMARY KEY,
    account_id          INTEGER NOT NULL REFERENCES client_accounts(account_id),
    instrument_id       INTEGER NOT NULL REFERENCES financial_instruments(instrument_id),
    quantity            NUMERIC(18,6) NOT NULL CHECK (quantity >= 0),
    as_of_timestamp     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_account_instrument UNIQUE (account_id, instrument_id)
);

-- -----------------------------------------------------------------------------
-- 6. CLIENT HOLDINGS
-- BRS Coverage: BR-10 (Real-time Holdings View)
-- Description: Aggregated position balances across all accounts owned by a single 
--              client for rapid client-level dashboard rendering.
-- -----------------------------------------------------------------------------
CREATE TABLE client_holdings (
    client_holding_id   SERIAL PRIMARY KEY,
    client_id           INTEGER NOT NULL REFERENCES client_profiles(client_id),
    instrument_id       INTEGER NOT NULL REFERENCES financial_instruments(instrument_id),
    total_quantity      NUMERIC(18,6) NOT NULL CHECK (total_quantity >= 0),
    as_of_timestamp     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_client_instrument UNIQUE (client_id, instrument_id)
);

-- -----------------------------------------------------------------------------
-- 7. MODEL PORTFOLIOS
-- BRS Coverage: Section 4.1 (Platform Scope Extension)
-- Description: Defines standardized investment strategies offered by the firm 
--              (e.g., Balanced Growth, Income Focus).
-- -----------------------------------------------------------------------------
CREATE TABLE model_portfolios (
    model_portfolio_id  SERIAL PRIMARY KEY,
    portfolio_name      TEXT NOT NULL UNIQUE,
    portfolio_description TEXT
);

-- -----------------------------------------------------------------------------
-- 8. MODEL PORTFOLIO HOLDINGS
-- BRS Coverage: Section 4.1 (Platform Scope Extension)
-- Description: Target asset allocation weights and effective dates for each 
--              instrument inside a model portfolio strategy.
-- -----------------------------------------------------------------------------
CREATE TABLE model_portfolio_holdings (
    model_portfolio_holding_id SERIAL PRIMARY KEY,
    model_portfolio_id  INTEGER NOT NULL REFERENCES model_portfolios(model_portfolio_id),
    instrument_id       INTEGER NOT NULL REFERENCES financial_instruments(instrument_id),
    effective_date      DATE NOT NULL,
    target_weight_pct   NUMERIC(5,2) NOT NULL CHECK (target_weight_pct BETWEEN 0 AND 100),
    CONSTRAINT uq_model_portfolio_holdings UNIQUE (model_portfolio_id, instrument_id, effective_date)
);

-- -----------------------------------------------------------------------------
-- 9. CLIENT SUBSCRIPTIONS
-- BRS Coverage: Section 4.1 (Platform Scope Extension)
-- Description: Links client accounts to target model portfolios and tracks 
--              active subscription date windows.
-- -----------------------------------------------------------------------------
CREATE TABLE client_subscriptions (
    client_subscription_id SERIAL PRIMARY KEY,
    client_id           INTEGER NOT NULL REFERENCES client_profiles(client_id),
    model_portfolio_id  INTEGER NOT NULL REFERENCES model_portfolios(model_portfolio_id),
    subscribed_from     DATE NOT NULL,
    subscribed_to       DATE,
    CONSTRAINT uq_client_subscriptions UNIQUE (client_id, subscribed_from)
);

-- -----------------------------------------------------------------------------
-- 10. MARKET QUOTES
-- BRS Coverage: BR-08 (Order Pricing), BR-13 (Indicative Price Display)
-- Description: Stores real-time streaming market price feeds (Bid/Ask). Provides 
--              indicative quotes to the UI and price discovery for trade fills.
-- -----------------------------------------------------------------------------
CREATE TABLE market_quotes (
    quote_id            BIGSERIAL PRIMARY KEY,
    instrument_id       INTEGER NOT NULL REFERENCES financial_instruments(instrument_id),
    bid_price           NUMERIC(16,4) NOT NULL CHECK (bid_price > 0),
    ask_price           NUMERIC(16,4) NOT NULL CHECK (ask_price > 0),
    quote_timestamp     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 11. ORDERS
-- BRS Coverage: BR-04 (Submission), BR-05 (Rule Validation), BR-06 (Intent Record), BR-07 (Status UI)
-- Description: Captures formal client trading intent prior to execution. Stores 
--              order parameters, pre-trade rule validation, and execution status.
-- -----------------------------------------------------------------------------
CREATE TABLE orders (
    order_id            BIGSERIAL PRIMARY KEY,
    client_id           INTEGER NOT NULL REFERENCES client_profiles(client_id),
    account_id          INTEGER NOT NULL REFERENCES client_accounts(account_id),
    instrument_id       INTEGER NOT NULL REFERENCES financial_instruments(instrument_id),
    order_side          VARCHAR(4) NOT NULL CHECK (order_side IN ('BUY', 'SELL')),
    order_type          VARCHAR(10) NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT')),
    requested_quantity  NUMERIC(18,6) NOT NULL CHECK (requested_quantity > 0),
    limit_price         NUMERIC(16,4) CHECK (limit_price > 0),
    order_status        VARCHAR(15) NOT NULL DEFAULT 'SUBMITTED' 
                        CHECK (order_status IN ('SUBMITTED', 'ACCEPTED', 'FILLED', 'REJECTED', 'CANCELLED')),
    rejection_reason    TEXT,
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 12. FILLS
-- BRS Coverage: BR-06 (Execution Separation), BR-08 (Execution Pricing), BR-09 (Atomic Balance Update)
-- Description: Immutable execution records generated when an order matches. 
--              Triggers atomic updates to cash, holdings, and transaction ledgers.
-- -----------------------------------------------------------------------------
CREATE TABLE fills (
    fill_id             BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(order_id),
    executed_quantity   NUMERIC(18,6) NOT NULL CHECK (executed_quantity > 0),
    executed_price      NUMERIC(16,4) NOT NULL CHECK (executed_price > 0),
    quote_id            BIGINT REFERENCES market_quotes(quote_id),
    executed_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 13. TRANSACTIONS
-- BRS Coverage: BR-09 (Atomic Ledger), BR-11 (Chronological Order & Fill History)
-- Description: Double-entry financial accounting ledger. Records all cash and 
--              security movements resulting from trade fills, deposits, or dividends.
-- -----------------------------------------------------------------------------
CREATE TABLE transactions (
    transaction_id      BIGSERIAL PRIMARY KEY,
    account_id          INTEGER NOT NULL REFERENCES client_accounts(account_id),
    instrument_id       INTEGER REFERENCES financial_instruments(instrument_id),
    fill_id             BIGINT REFERENCES fills(fill_id),
    transaction_type    VARCHAR(15) NOT NULL CHECK (transaction_type IN ('BUY', 'SELL', 'DIVIDEND', 'DEPOSIT', 'WITHDRAWAL')),
    quantity            NUMERIC(18,6),
    unit_price          NUMERIC(16,4),
    net_amount          NUMERIC(16,4) NOT NULL,
    transaction_date    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 14. AUDIT LOGS
-- BRS Coverage: BR-14 (Permanent Record), BR-15 (Trade Reconstruction / Audit)
-- Description: Append-only system audit trail logging all state transitions, 
--              pricing decisions, and data modifications for legal compliance.
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    audit_id            BIGSERIAL PRIMARY KEY,
    entity_name         VARCHAR(50) NOT NULL,
    entity_id           BIGINT NOT NULL,
    action_type         VARCHAR(10) NOT NULL CHECK (action_type IN ('INSERT', 'UPDATE', 'DELETE')),
    client_id           INTEGER REFERENCES client_profiles(client_id),
    state_before        JSONB,
    state_after         JSONB,
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- PERFORMANCE & LOOKUP INDEXES
CREATE INDEX idx_orders_client_id ON orders(client_id);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_fills_order_id ON fills(order_id);
CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_market_quotes_lookup ON market_quotes(instrument_id, quote_timestamp DESC);
CREATE INDEX idx_audit_lookup ON audit_logs(entity_name, entity_id);