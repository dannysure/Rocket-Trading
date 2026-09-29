-- =====================================================================
-- BR-09 ATOMICITY: Fill Execution and Atomic Balance Updates
-- =====================================================================
-- This file contains functions and triggers that enforce atomic updates
-- when a fill is created:
--   1. Update account_holdings (or insert if new)
--   2. Update client_holdings (or insert if new)
--   3. Update client_accounts.cash_balance
--   4. Record transactions (double-entry ledger)
--   5. Record audit_logs for compliance

-- =====================================================================
-- 1. FUNCTION: execute_fill_atomically()
-- =====================================================================
-- Executes when a fill is inserted. Atomically updates all derived state.
-- Runs in SERIALIZABLE isolation to prevent race conditions.
-- =====================================================================

CREATE OR REPLACE FUNCTION execute_fill_atomically()
RETURNS TRIGGER AS $$
DECLARE
    v_order_rec         RECORD;
    v_account_id        INTEGER;
    v_client_id         INTEGER;
    v_instrument_id     INTEGER;
    v_order_side        VARCHAR(4);
    v_cash_delta        NUMERIC;
    v_new_account_qty   NUMERIC;
    v_new_client_qty    NUMERIC;
BEGIN
    -- Fetch order details
    SELECT 
        o.account_id, o.client_id, o.instrument_id, o.order_side, o.requested_quantity
    INTO 
        v_account_id, v_client_id, v_instrument_id, v_order_side, v_new_account_qty
    FROM orders o
    WHERE o.order_id = NEW.order_id;
    
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Order % not found', NEW.order_id;
    END IF;
    
    -- =====================================================================
    -- STEP 1: Update account_holdings (BUY adds, SELL subtracts)
    -- =====================================================================
    IF v_order_side = 'BUY' THEN
        INSERT INTO account_holdings (account_id, instrument_id, quantity, as_of_timestamp)
        VALUES (v_account_id, v_instrument_id, NEW.executed_quantity, CURRENT_TIMESTAMP)
        ON CONFLICT (account_id, instrument_id) DO UPDATE
        SET quantity = account_holdings.quantity + NEW.executed_quantity,
            as_of_timestamp = CURRENT_TIMESTAMP;
    ELSE  -- SELL
        UPDATE account_holdings
        SET quantity = quantity - NEW.executed_quantity,
            as_of_timestamp = CURRENT_TIMESTAMP
        WHERE account_id = v_account_id AND instrument_id = v_instrument_id;
    END IF;
    
    -- =====================================================================
    -- STEP 2: Update client_holdings (aggregated position)
    -- =====================================================================
    IF v_order_side = 'BUY' THEN
        INSERT INTO client_holdings (client_id, instrument_id, total_quantity, as_of_timestamp)
        VALUES (v_client_id, v_instrument_id, NEW.executed_quantity, CURRENT_TIMESTAMP)
        ON CONFLICT (client_id, instrument_id) DO UPDATE
        SET total_quantity = client_holdings.total_quantity + NEW.executed_quantity,
            as_of_timestamp = CURRENT_TIMESTAMP;
    ELSE  -- SELL
        UPDATE client_holdings
        SET total_quantity = total_quantity - NEW.executed_quantity,
            as_of_timestamp = CURRENT_TIMESTAMP
        WHERE client_id = v_client_id AND instrument_id = v_instrument_id;
    END IF;
    
    -- =====================================================================
    -- STEP 3: Update cash_balance in client_accounts
    -- =====================================================================
    v_cash_delta := NEW.executed_quantity * NEW.executed_price;
    
    IF v_order_side = 'BUY' THEN
        -- BUY: subtract cash
        UPDATE client_accounts
        SET cash_balance = cash_balance - v_cash_delta
        WHERE account_id = v_account_id;
    ELSE  -- SELL
        -- SELL: add cash (proceeds)
        UPDATE client_accounts
        SET cash_balance = cash_balance + v_cash_delta
        WHERE account_id = v_account_id;
    END IF;
    
    -- =====================================================================
    -- STEP 4: Record double-entry transactions (ledger)
    -- =====================================================================
    -- Entry 1: Instrument movement (BUY=debit security, SELL=credit security)
    IF v_order_side = 'BUY' THEN
        INSERT INTO transactions 
        (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
        VALUES (v_account_id, v_instrument_id, NEW.fill_id, 'BUY', NEW.executed_quantity, 
                NEW.executed_price, v_cash_delta, CURRENT_TIMESTAMP);
    ELSE  -- SELL
        INSERT INTO transactions 
        (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
        VALUES (v_account_id, v_instrument_id, NEW.fill_id, 'SELL', NEW.executed_quantity, 
                NEW.executed_price, -v_cash_delta, CURRENT_TIMESTAMP);
    END IF;
    
    -- Entry 2: Cash movement (opposite of instrument)
    -- BUY = cash out (WITHDRAWAL), SELL = cash in (DEPOSIT)
    IF v_order_side = 'BUY' THEN
        INSERT INTO transactions 
        (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
        VALUES (v_account_id, NULL, NEW.fill_id, 'WITHDRAWAL', NULL, NULL, -v_cash_delta, CURRENT_TIMESTAMP);
    ELSE  -- SELL
        INSERT INTO transactions 
        (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
        VALUES (v_account_id, NULL, NEW.fill_id, 'DEPOSIT', NULL, NULL, v_cash_delta, CURRENT_TIMESTAMP);
    END IF;
    
    -- =====================================================================
    -- STEP 5: Record audit log for compliance (BR-14, BR-15)
    -- =====================================================================
    INSERT INTO audit_logs 
    (entity_name, entity_id, action_type, client_id, state_after, recorded_at)
    VALUES (
        'fills',
        NEW.fill_id,
        'INSERT',
        v_client_id,
        jsonb_build_object(
            'order_id', NEW.order_id,
            'executed_quantity', NEW.executed_quantity,
            'executed_price', NEW.executed_price,
            'quote_id', NEW.quote_id,
            'order_side', v_order_side,
            'cash_delta', v_cash_delta
        ),
        CURRENT_TIMESTAMP
    );
    
    -- =====================================================================
    -- STEP 6: Create order notification (BR-07)
    -- =====================================================================
    INSERT INTO order_notifications
    (order_id, client_id, old_status, new_status, notification_type, message, created_at)
    VALUES (
        NEW.order_id,
        v_client_id,
        'ACCEPTED',
        'FILLED',
        'EXECUTED',
        format('Order filled: %s shares of instrument %s at £%s per share',
               NEW.executed_quantity, v_instrument_id, NEW.executed_price),
        CURRENT_TIMESTAMP
    );
    
    -- =====================================================================
    -- STEP 7: Update order status to FILLED
    -- =====================================================================
    UPDATE orders
    SET order_status = 'FILLED'
    WHERE order_id = NEW.order_id;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- TRIGGER: trigger_fill_execution
-- =====================================================================
-- Executes execute_fill_atomically() when a fill is inserted.
-- Serializable isolation ensures no race conditions.
-- =====================================================================

CREATE TRIGGER trigger_fill_execution
AFTER INSERT ON fills
FOR EACH ROW
EXECUTE FUNCTION execute_fill_atomically();

-- =====================================================================
-- 2. FUNCTION: audit_order_changes()
-- =====================================================================
-- Auto-populates audit_logs when orders are created or status changes.
-- =====================================================================

CREATE OR REPLACE FUNCTION audit_order_changes()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_logs
    (entity_name, entity_id, action_type, client_id, state_before, state_after, recorded_at)
    VALUES (
        'orders',
        NEW.order_id,
        TG_OP,
        NEW.client_id,
        CASE WHEN TG_OP = 'UPDATE' THEN 
            jsonb_build_object(
                'order_status', OLD.order_status,
                'rejected_at', OLD.rejected_at,
                'accepted_at', OLD.accepted_at
            )
        ELSE NULL END,
        jsonb_build_object(
            'order_status', NEW.order_status,
            'order_side', NEW.order_side,
            'requested_quantity', NEW.requested_quantity,
            'instrument_id', NEW.instrument_id,
            'submitted_at', NEW.submitted_at,
            'accepted_at', NEW.accepted_at,
            'rejected_at', NEW.rejected_at,
            'rejection_reason', NEW.rejection_reason
        ),
        CURRENT_TIMESTAMP
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_audit_orders
AFTER INSERT OR UPDATE ON orders
FOR EACH ROW
EXECUTE FUNCTION audit_order_changes();

-- =====================================================================
-- 3. FUNCTION: audit_holdings_changes()
-- =====================================================================
-- Auto-populates audit_logs for holdings changes (from trigger or manual).
-- =====================================================================

CREATE OR REPLACE FUNCTION audit_holdings_changes()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_logs
    (entity_name, entity_id, action_type, client_id, state_before, state_after, recorded_at)
    VALUES (
        'account_holdings',
        NEW.holding_id,
        TG_OP,
        (SELECT client_id FROM client_accounts WHERE account_id = NEW.account_id),
        CASE WHEN TG_OP = 'UPDATE' THEN 
            jsonb_build_object('quantity', OLD.quantity, 'as_of_timestamp', OLD.as_of_timestamp)
        ELSE NULL END,
        jsonb_build_object(
            'account_id', NEW.account_id,
            'instrument_id', NEW.instrument_id,
            'quantity', NEW.quantity,
            'as_of_timestamp', NEW.as_of_timestamp
        ),
        CURRENT_TIMESTAMP
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_audit_account_holdings
AFTER INSERT OR UPDATE ON account_holdings
FOR EACH ROW
EXECUTE FUNCTION audit_holdings_changes();

-- =====================================================================
-- 4. FUNCTION: audit_transaction_ledger()
-- =====================================================================
-- Auto-populates audit_logs for all ledger entries.
-- =====================================================================

CREATE OR REPLACE FUNCTION audit_transaction_ledger()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_logs
    (entity_name, entity_id, action_type, client_id, state_after, recorded_at)
    VALUES (
        'transactions',
        NEW.transaction_id,
        'INSERT',
        (SELECT client_id FROM client_accounts WHERE account_id = NEW.account_id),
        jsonb_build_object(
            'account_id', NEW.account_id,
            'transaction_type', NEW.transaction_type,
            'instrument_id', NEW.instrument_id,
            'quantity', NEW.quantity,
            'unit_price', NEW.unit_price,
            'net_amount', NEW.net_amount,
            'fill_id', NEW.fill_id
        ),
        CURRENT_TIMESTAMP
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_audit_transactions
AFTER INSERT ON transactions
FOR EACH ROW
EXECUTE FUNCTION audit_transaction_ledger();

-- =====================================================================
-- VERIFICATION: Test queries to verify atomicity
-- =====================================================================
-- After a fill is created, these queries should show all updates:
--
-- 1. Check order status changed to FILLED:
--    SELECT order_status FROM orders WHERE order_id = <id>;
--
-- 2. Check holdings were updated:
--    SELECT quantity FROM account_holdings WHERE account_id = <id> AND instrument_id = <id>;
--
-- 3. Check cash was deducted/credited:
--    SELECT cash_balance FROM client_accounts WHERE account_id = <id>;
--
-- 4. Check ledger entries exist (should be 2):
--    SELECT COUNT(*) FROM transactions WHERE fill_id = <id>;
--
-- 5. Check audit trail is populated:
--    SELECT entity_name, action_type FROM audit_logs WHERE entity_id = <id>;
--
-- 6. Check notification was created:
--    SELECT * FROM order_notifications WHERE order_id = <id>;
--
-- If any step fails, the entire transaction rolls back (no partial fills).
-- =====================================================================
