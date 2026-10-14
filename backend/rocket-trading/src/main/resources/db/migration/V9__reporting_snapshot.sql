-- BR-16: reporting reads a periodically refreshed snapshot, never the live order tables,
-- so analyst queries cannot lock or slow client order flow. Refreshed CONCURRENTLY by ReportingSnapshot.
CREATE MATERIALIZED VIEW reporting_order_facts AS
SELECT o.order_id,
       o.client_id,
       cp.risk_profile,
       fi.ticker_symbol AS symbol,
       fi.asset_class,
       fi.market_segment,
       o.order_side,
       o.order_status,
       o.requested_quantity,
       o.submitted_at,
       f.fill_id,
       f.executed_at,
       coalesce(f.settlement_amount, f.executed_quantity * f.executed_price) AS settled_notional
FROM orders o
JOIN client_profiles cp ON cp.client_id = o.client_id
JOIN financial_instruments fi ON fi.instrument_id = o.instrument_id
LEFT JOIN fills f ON f.order_id = o.order_id
WITH DATA;

-- Required for REFRESH ... CONCURRENTLY; one row per order because fills are unique per order
CREATE UNIQUE INDEX uq_reporting_order_facts ON reporting_order_facts (order_id);
CREATE INDEX idx_reporting_order_facts_submitted ON reporting_order_facts (submitted_at);
