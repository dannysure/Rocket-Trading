-- BR-16: reporting moved to its own database (see db/reporting and ReportingStore). The snapshot
-- that lived here was rebuilt from the live order tables every 30 seconds, on the trading server.
DROP MATERIALIZED VIEW IF EXISTS reporting_order_facts;
