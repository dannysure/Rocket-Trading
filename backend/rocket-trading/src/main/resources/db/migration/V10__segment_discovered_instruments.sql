-- Instruments auto-registered before V7 were all placed in the US segment. Their trading currency
-- (or listing suffix) says which market they really belong to.
UPDATE financial_instruments
SET market_segment = 'IN', listing_exchange = coalesce(listing_exchange, 'NSE')
WHERE asset_class = 'Equity'
  AND (base_currency = 'INR' OR ticker_symbol LIKE '%.NS' OR ticker_symbol LIKE '%.BO');

UPDATE financial_instruments
SET market_segment = 'UK', listing_exchange = coalesce(listing_exchange, 'LSE')
WHERE asset_class = 'Equity'
  AND (base_currency IN ('GBP', 'GBX') OR ticker_symbol LIKE '%.L');
