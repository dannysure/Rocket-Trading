-- Fauxnance only recognizes crypto pairs in its canonical X:<BASE>-<QUOTE> form
UPDATE financial_instruments SET ticker_symbol = 'X:BTC-USD' WHERE ticker_symbol = 'BTCUSD';
UPDATE financial_instruments SET ticker_symbol = 'X:ETH-USD' WHERE ticker_symbol = 'ETHUSD';
