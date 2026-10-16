-- Funding follows open interest: the long/short imbalance (skew) divided by skew_scale is the premium of the
-- perp over its spot index, capped at 1%. skew_scale is the net size, in the base asset, that would move the
-- mark 100% away from the index, so larger values mean a deeper market (about $80M of BTC and $70M of ETH here).
ALTER TABLE perp_markets ADD COLUMN skew_scale NUMERIC(20,6) NOT NULL DEFAULT 1000 CHECK (skew_scale > 0);
UPDATE perp_markets SET skew_scale = 30000 WHERE market_symbol = 'ETH-PERP';
