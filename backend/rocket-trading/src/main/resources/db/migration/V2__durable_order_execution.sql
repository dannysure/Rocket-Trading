-- Existing installations require an explicit V1 baseline after backup/review.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trigger_fill_execution' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Legacy settlement trigger installed. Review docs/db-backend-connection.md before migration.';
    END IF;
END $$;

ALTER TABLE orders ADD COLUMN market VARCHAR(10);
UPDATE orders o SET market = CASE WHEN fi.asset_class = 'Crypto' THEN 'crypto' ELSE 'stock' END
FROM financial_instruments fi WHERE fi.instrument_id = o.instrument_id;
ALTER TABLE orders ALTER COLUMN market SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT supported_market CHECK (market IN ('stock', 'crypto'));
ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE orders ADD COLUMN request_fingerprint TEXT;
ALTER TABLE orders ADD CONSTRAINT unique_client_request UNIQUE (client_id, idempotency_key);
-- This phase supports a single full fill per order.
ALTER TABLE fills ADD CONSTRAINT unique_order_fill UNIQUE (order_id);
CREATE INDEX idx_pending_orders ON orders (order_id) WHERE order_status = 'ACCEPTED';
CREATE UNIQUE INDEX uq_direct_trading_account ON client_accounts(client_id) WHERE account_type = 'DIRECT_TRADING';
CREATE UNIQUE INDEX uq_client_email_normalized ON client_profiles(lower(email_address));

ALTER TABLE client_profiles ALTER COLUMN registered_at TYPE TIMESTAMPTZ USING registered_at AT TIME ZONE 'UTC';
ALTER TABLE client_sessions ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC';
ALTER TABLE account_holdings ALTER COLUMN as_of_timestamp TYPE TIMESTAMPTZ USING as_of_timestamp AT TIME ZONE 'UTC';
ALTER TABLE client_holdings ALTER COLUMN as_of_timestamp TYPE TIMESTAMPTZ USING as_of_timestamp AT TIME ZONE 'UTC';
ALTER TABLE market_quotes ALTER COLUMN quote_timestamp TYPE TIMESTAMPTZ USING quote_timestamp AT TIME ZONE 'UTC';
ALTER TABLE orders ALTER COLUMN submitted_at TYPE TIMESTAMPTZ USING submitted_at AT TIME ZONE 'UTC';
ALTER TABLE fills ALTER COLUMN executed_at TYPE TIMESTAMPTZ USING executed_at AT TIME ZONE 'UTC';
ALTER TABLE transactions ALTER COLUMN transaction_date TYPE TIMESTAMPTZ USING transaction_date AT TIME ZONE 'UTC';
ALTER TABLE audit_logs ALTER COLUMN recorded_at TYPE TIMESTAMPTZ USING recorded_at AT TIME ZONE 'UTC';

-- These only protect historical records; settlement business logic is in Spring.
CREATE FUNCTION prevent_record_changes() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% records are append-only', TG_TABLE_NAME;
END $$;
CREATE TRIGGER protect_audit BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION prevent_record_changes();
CREATE TRIGGER protect_fills BEFORE UPDATE OR DELETE ON fills FOR EACH ROW EXECUTE FUNCTION prevent_record_changes();
CREATE TRIGGER protect_ledger BEFORE UPDATE OR DELETE ON transactions FOR EACH ROW EXECUTE FUNCTION prevent_record_changes();
CREATE TRIGGER protect_quotes BEFORE UPDATE OR DELETE ON market_quotes FOR EACH ROW EXECUTE FUNCTION prevent_record_changes();
CREATE TRIGGER protect_orders BEFORE DELETE ON orders FOR EACH ROW EXECUTE FUNCTION prevent_record_changes();
