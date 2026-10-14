-- BR-12: US, UK and Indian equities, FX and crypto, all settled in the account currency

ALTER TABLE financial_instruments ADD COLUMN market_segment VARCHAR(10);
ALTER TABLE financial_instruments ADD COLUMN listing_exchange TEXT;
UPDATE financial_instruments
SET market_segment = CASE asset_class WHEN 'Crypto' THEN 'CRYPTO' WHEN 'FX' THEN 'FX' ELSE 'US' END;
ALTER TABLE financial_instruments ALTER COLUMN market_segment SET NOT NULL;
ALTER TABLE financial_instruments ADD CONSTRAINT supported_segment
    CHECK (market_segment IN ('US', 'UK', 'IN', 'FX', 'CRYPTO'));

-- The quote provider carries no LSE listings yet, so UK companies trade through their US listings
INSERT INTO financial_instruments (ticker_symbol, instrument_name, asset_class, base_currency, is_tradable, market_segment, listing_exchange)
VALUES
    ('NVDA', 'NVIDIA Corporation', 'Equity', 'USD', TRUE, 'US', 'NASDAQ'),
    ('AMZN', 'Amazon.com, Inc.', 'Equity', 'USD', TRUE, 'US', 'NASDAQ'),
    ('TSLA', 'Tesla, Inc.', 'Equity', 'USD', TRUE, 'US', 'NASDAQ'),
    ('BP', 'BP p.l.c. (US listing)', 'Equity', 'USD', TRUE, 'UK', 'NYSE'),
    ('SHEL', 'Shell plc (US listing)', 'Equity', 'USD', TRUE, 'UK', 'NYSE'),
    ('VOD', 'Vodafone Group plc (US listing)', 'Equity', 'USD', TRUE, 'UK', 'NASDAQ'),
    ('HSBC', 'HSBC Holdings plc (US listing)', 'Equity', 'USD', TRUE, 'UK', 'NYSE'),
    ('AZN', 'AstraZeneca PLC (US listing)', 'Equity', 'USD', TRUE, 'UK', 'NYSE'),
    ('RELIANCE.NS', 'Reliance Industries', 'Equity', 'INR', TRUE, 'IN', 'NSE'),
    ('TCS.NS', 'Tata Consultancy Services', 'Equity', 'INR', TRUE, 'IN', 'NSE'),
    ('INFY.NS', 'Infosys', 'Equity', 'INR', TRUE, 'IN', 'NSE'),
    ('HDFCBANK.NS', 'HDFC Bank', 'Equity', 'INR', TRUE, 'IN', 'NSE'),
    ('FX:EURUSD', 'Euro / US Dollar', 'FX', 'USD', TRUE, 'FX', 'FX'),
    ('FX:GBPUSD', 'British Pound / US Dollar', 'FX', 'USD', TRUE, 'FX', 'FX'),
    ('FX:USDJPY', 'US Dollar / Japanese Yen', 'FX', 'JPY', TRUE, 'FX', 'FX'),
    ('FX:USDINR', 'US Dollar / Indian Rupee', 'FX', 'INR', TRUE, 'FX', 'FX'),
    ('X:SOL-USD', 'Solana', 'Crypto', 'USD', TRUE, 'CRYPTO', 'CRYPTO')
ON CONFLICT (ticker_symbol) DO NOTHING;
UPDATE financial_instruments SET listing_exchange = 'NASDAQ' WHERE ticker_symbol IN ('AAPL', 'MSFT', 'GOOGL');
UPDATE financial_instruments SET listing_exchange = 'CRYPTO' WHERE asset_class = 'Crypto' AND listing_exchange IS NULL;

ALTER TABLE orders DROP CONSTRAINT supported_market;
ALTER TABLE orders ADD CONSTRAINT supported_market CHECK (market IN ('stock', 'crypto', 'fx'));

-- BR-06: an accepted order with no current market price waits for one instead of failing
ALTER TABLE orders ADD COLUMN next_execution_at TIMESTAMPTZ;
DROP INDEX idx_pending_orders;
CREATE INDEX idx_pending_orders ON orders (next_execution_at NULLS FIRST, order_id) WHERE order_status = 'ACCEPTED';

-- Fills keep the instrument-currency price and the FX rate used to settle in the account currency.
-- Fills before this migration were all USD instruments in USD accounts (rate 1).
ALTER TABLE fills ADD COLUMN instrument_currency VARCHAR(3);
ALTER TABLE fills ADD COLUMN settlement_currency VARCHAR(3);
ALTER TABLE fills ADD COLUMN fx_rate NUMERIC(20,10);
ALTER TABLE fills ADD COLUMN settlement_amount NUMERIC(16,4);

-- Average cost per unit in the account currency; sells leave it unchanged
ALTER TABLE account_holdings ADD COLUMN average_cost NUMERIC(20,8);
UPDATE account_holdings h
SET average_cost = s.cost / s.qty
FROM (
    SELECT account_id, instrument_id, sum(net_amount) AS cost, sum(quantity) AS qty
    FROM transactions
    WHERE transaction_type = 'BUY'
    GROUP BY account_id, instrument_id
) s
WHERE s.account_id = h.account_id AND s.instrument_id = h.instrument_id AND s.qty > 0;

-- Opening funding becomes a ledger entry so cash is fully explained by the ledger and total return is computable
INSERT INTO transactions (account_id, transaction_type, net_amount, transaction_date)
SELECT a.account_id,
       'DEPOSIT',
       a.cash_balance + coalesce(sum(CASE t.transaction_type
                                         WHEN 'BUY' THEN t.net_amount
                                         WHEN 'SELL' THEN -t.net_amount
                                         ELSE 0 END), 0),
       a.opened_date
FROM client_accounts a
LEFT JOIN transactions t ON t.account_id = a.account_id
WHERE NOT EXISTS (
    SELECT 1 FROM transactions d WHERE d.account_id = a.account_id AND d.transaction_type = 'DEPOSIT'
)
GROUP BY a.account_id, a.cash_balance, a.opened_date;
