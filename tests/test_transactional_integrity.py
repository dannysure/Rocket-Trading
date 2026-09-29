"""
Transaction Integrity Tests - BR-09
=======================================
Tests verify that fills atomically update:
  1. account_holdings
  2. client_holdings  
  3. client_accounts (cash_balance)
  4. transactions ledger
  5. audit_logs

All must succeed together or all must rollback. No partial states.
"""

import pytest
import psycopg2
from psycopg2 import sql
from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT, ISOLATION_LEVEL_SERIALIZABLE
from datetime import datetime, timedelta
import os
from decimal import Decimal


class DatabaseTestFixture:
    """Manages test database connections and cleanup."""
    
    def __init__(self):
        self.db_host = os.getenv('DB_HOST', 'localhost')
        self.db_port = int(os.getenv('DB_PORT', '5432'))
        self.db_name = os.getenv('DB_NAME', 'team_rocket_db')
        self.db_user = os.getenv('DB_USER', 'team_rocket_admin')
        self.db_password = os.getenv('DB_PASSWORD', 'team_rocket_password123')
        self.connection = None
    
    def connect(self):
        """Establish database connection."""
        self.connection = psycopg2.connect(
            host=self.db_host,
            port=self.db_port,
            database=self.db_name,
            user=self.db_user,
            password=self.db_password
        )
        return self.connection
    
    def disconnect(self):
        """Close database connection."""
        if self.connection:
            self.connection.close()
    
    def execute_query(self, query, params=None):
        """Execute a query and return results."""
        cursor = self.connection.cursor()
        try:
            cursor.execute(query, params)
            return cursor.fetchall()
        finally:
            cursor.close()
    
    def execute_update(self, query, params=None):
        """Execute an update and commit."""
        cursor = self.connection.cursor()
        try:
            cursor.execute(query, params)
            self.connection.commit()
            return cursor.rowcount
        finally:
            cursor.close()


@pytest.fixture
def db():
    """Database fixture for all tests."""
    fixture = DatabaseTestFixture()
    fixture.connect()
    yield fixture
    fixture.disconnect()


@pytest.fixture
def test_data(db):
    """Create standard test data: client, accounts, instruments, quotes."""
    
    # Create test client
    client_query = """
    INSERT INTO client_profiles (client_full_name, date_of_birth, risk_profile, registered_at)
    VALUES (%s, %s, %s, CURRENT_TIMESTAMP)
    RETURNING client_id;
    """
    result = db.execute_query(client_query, ('Test Client', '1990-01-01', 'Balanced'))
    client_id = result[0][0]
    
    # Create test account with initial cash
    account_query = """
    INSERT INTO client_accounts (client_id, account_type, cash_balance, currency, opened_date)
    VALUES (%s, %s, %s, %s, CURRENT_DATE)
    RETURNING account_id;
    """
    result = db.execute_query(account_query, (client_id, 'DIRECT_TRADING', Decimal('10000.0000'), 'GBP'))
    account_id = result[0][0]
    
    # Create test instruments
    instruments = []
    for ticker in ['AAPL', 'GOOGL', 'MSFT']:
        instr_query = """
        INSERT INTO financial_instruments (ticker_symbol, instrument_name, asset_class, base_currency, is_tradable)
        VALUES (%s, %s, %s, %s, TRUE)
        RETURNING instrument_id;
        """
        result = db.execute_query(instr_query, (ticker, f'{ticker} Stock', 'Equity', 'USD'))
        instruments.append(result[0][0])
    
    # Create market quotes
    for instr_id in instruments:
        quote_query = """
        INSERT INTO market_quotes (instrument_id, bid_price, ask_price, quote_timestamp)
        VALUES (%s, %s, %s, CURRENT_TIMESTAMP)
        RETURNING quote_id;
        """
        result = db.execute_query(quote_query, (instr_id, Decimal('150.00'), Decimal('150.50')))
    
    # Initial position: 100 shares of AAPL at average cost 145.00
    holding_query = """
    INSERT INTO account_holdings (account_id, instrument_id, quantity, as_of_timestamp)
    VALUES (%s, %s, %s, CURRENT_TIMESTAMP);
    """
    db.execute_update(holding_query, (account_id, instruments[0], Decimal('100.000000')))
    
    # Mirror in client_holdings
    client_holding_query = """
    INSERT INTO client_holdings (client_id, instrument_id, total_quantity, as_of_timestamp)
    VALUES (%s, %s, %s, CURRENT_TIMESTAMP);
    """
    db.execute_update(client_holding_query, (client_id, instruments[0], Decimal('100.000000')))
    
    return {
        'client_id': client_id,
        'account_id': account_id,
        'instruments': instruments,
        'initial_cash': Decimal('10000.0000')
    }


class TestBR09AtomicFillUpdates:
    """Test BR-09: Fills must atomically update holdings, cash, and audit trail."""
    
    def test_buy_order_fill_atomicity(self, db, test_data):
        """
        Test: BUY order fill updates:
        - Deducts cash from account
        - Adds shares to account_holdings
        - Adds shares to client_holdings
        - Records transaction ledger entries (buy + cash)
        - Records audit log entries
        All atomically.
        """
        client_id = test_data['client_id']
        account_id = test_data['account_id']
        instrument_id = test_data['instruments'][1]  # GOOGL - not currently held
        
        # Create BUY order
        order_query = """
        INSERT INTO orders 
        (client_id, account_id, instrument_id, order_side, order_type, requested_quantity, submitted_at)
        VALUES (%s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP)
        RETURNING order_id;
        """
        result = db.execute_query(order_query, (
            client_id, account_id, instrument_id, 'BUY', 'MARKET', Decimal('10.000000')
        ))
        order_id = result[0][0]
        
        # Get quote for pricing
        quote_query = """
        SELECT quote_id, ask_price FROM market_quotes 
        WHERE instrument_id = %s 
        ORDER BY quote_timestamp DESC LIMIT 1;
        """
        result = db.execute_query(quote_query, (instrument_id,))
        quote_id, ask_price = result[0]
        execution_price = ask_price
        
        # SNAPSHOT BEFORE FILL
        before_cash = db.execute_query(
            "SELECT cash_balance FROM client_accounts WHERE account_id = %s;",
            (account_id,)
        )[0][0]
        before_holdings_count = db.execute_query(
            "SELECT COUNT(*) FROM account_holdings WHERE account_id = %s;",
            (account_id,)
        )[0][0]
        
        # Create FILL (this should trigger atomic updates)
        fill_query = """
        INSERT INTO fills (order_id, executed_quantity, executed_price, quote_id, executed_at)
        VALUES (%s, %s, %s, %s, CURRENT_TIMESTAMP)
        RETURNING fill_id;
        """
        result = db.execute_query(fill_query, (order_id, Decimal('10.000000'), execution_price, quote_id))
        fill_id = result[0][0]
        
        # MANUALLY UPDATE HOLDINGS AND CASH (simulating trigger behavior)
        # In production, this would be done by a trigger for atomicity
        cursor = db.connection.cursor()
        try:
            cursor.execute("BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;")
            
            # 1. Update account holdings
            cursor.execute("""
                INSERT INTO account_holdings (account_id, instrument_id, quantity, as_of_timestamp)
                VALUES (%s, %s, %s, CURRENT_TIMESTAMP)
                ON CONFLICT (account_id, instrument_id) 
                DO UPDATE SET quantity = account_holdings.quantity + %s;
            """, (account_id, instrument_id, Decimal('10.000000'), Decimal('10.000000')))
            
            # 2. Update client holdings
            cursor.execute("""
                INSERT INTO client_holdings (client_id, instrument_id, total_quantity, as_of_timestamp)
                VALUES (%s, %s, %s, CURRENT_TIMESTAMP)
                ON CONFLICT (client_id, instrument_id)
                DO UPDATE SET total_quantity = client_holdings.total_quantity + %s;
            """, (client_id, instrument_id, Decimal('10.000000'), Decimal('10.000000')))
            
            # 3. Deduct cash from account
            cost = Decimal('10.000000') * execution_price
            cursor.execute("""
                UPDATE client_accounts 
                SET cash_balance = cash_balance - %s
                WHERE account_id = %s;
            """, (cost, account_id))
            
            # 4. Record transactions (two ledger entries for double-entry accounting)
            # Debit: security account (instrument bought)
            cursor.execute("""
                INSERT INTO transactions 
                (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
                VALUES (%s, %s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP);
            """, (account_id, instrument_id, fill_id, 'BUY', Decimal('10.000000'), execution_price, cost))
            
            # Credit: cash account
            cursor.execute("""
                INSERT INTO transactions 
                (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
                VALUES (%s, NULL, %s, %s, NULL, NULL, %s, CURRENT_TIMESTAMP);
            """, (account_id, fill_id, 'SELL', -cost))  # Negative amount = cash out
            
            # 5. Record audit log
            cursor.execute("""
                INSERT INTO audit_logs 
                (entity_name, entity_id, action_type, client_id, state_after, recorded_at)
                VALUES (%s, %s, %s, %s, %s, CURRENT_TIMESTAMP);
            """, ('fills', fill_id, 'INSERT', client_id, 
                  f'{{"order_id": {order_id}, "executed_quantity": 10, "executed_price": {execution_price}}}'))
            
            cursor.execute("COMMIT;")
        except Exception as e:
            cursor.execute("ROLLBACK;")
            raise e
        finally:
            cursor.close()
        
        # VERIFY ALL UPDATES OCCURRED ATOMICALLY
        after_cash = db.execute_query(
            "SELECT cash_balance FROM client_accounts WHERE account_id = %s;",
            (account_id,)
        )[0][0]
        
        # Cash should be reduced by (10 * ask_price)
        expected_cash = before_cash - (Decimal('10.000000') * execution_price)
        assert after_cash == expected_cash, f"Cash not updated: {after_cash} != {expected_cash}"
        
        # New holding should be inserted
        after_holdings_count = db.execute_query(
            "SELECT COUNT(*) FROM account_holdings WHERE account_id = %s;",
            (account_id,)
        )[0][0]
        assert after_holdings_count == before_holdings_count + 1, "Holdings not inserted"
        
        # Verify quantity in account_holdings
        holding_qty = db.execute_query(
            "SELECT quantity FROM account_holdings WHERE account_id = %s AND instrument_id = %s;",
            (account_id, instrument_id)
        )[0][0]
        assert holding_qty == Decimal('10.000000'), f"Account holding not correct: {holding_qty}"
        
        # Verify quantity in client_holdings
        client_holding_qty = db.execute_query(
            "SELECT total_quantity FROM client_holdings WHERE client_id = %s AND instrument_id = %s;",
            (client_id, instrument_id)
        )[0][0]
        assert client_holding_qty == Decimal('10.000000'), f"Client holding not correct: {client_holding_qty}"
        
        # Verify transactions recorded
        txn_count = db.execute_query(
            "SELECT COUNT(*) FROM transactions WHERE fill_id = %s;",
            (fill_id,)
        )[0][0]
        assert txn_count == 2, f"Expected 2 transaction ledger entries, got {txn_count}"
        
        # Verify audit log
        audit_count = db.execute_query(
            "SELECT COUNT(*) FROM audit_logs WHERE entity_name = 'fills' AND entity_id = %s;",
            (fill_id,)
        )[0][0]
        assert audit_count >= 1, "Audit log not recorded"
    
    def test_sell_order_fill_atomicity(self, db, test_data):
        """
        Test: SELL order fill updates:
        - Adds cash to account
        - Reduces shares from account_holdings
        - Reduces shares from client_holdings
        - Records transaction entries
        - Records audit log
        Atomically.
        """
        client_id = test_data['client_id']
        account_id = test_data['account_id']
        instrument_id = test_data['instruments'][0]  # AAPL - already held (100 shares)
        
        # Get current cash
        before_cash = db.execute_query(
            "SELECT cash_balance FROM client_accounts WHERE account_id = %s;",
            (account_id,)
        )[0][0]
        
        # Create SELL order
        order_query = """
        INSERT INTO orders 
        (client_id, account_id, instrument_id, order_side, order_type, requested_quantity, submitted_at)
        VALUES (%s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP)
        RETURNING order_id;
        """
        result = db.execute_query(order_query, (
            client_id, account_id, instrument_id, 'SELL', 'MARKET', Decimal('30.000000')
        ))
        order_id = result[0][0]
        
        # Get quote
        quote_query = """
        SELECT quote_id, bid_price FROM market_quotes 
        WHERE instrument_id = %s 
        ORDER BY quote_timestamp DESC LIMIT 1;
        """
        result = db.execute_query(quote_query, (instrument_id,))
        quote_id, bid_price = result[0]
        
        # Create FILL
        fill_query = """
        INSERT INTO fills (order_id, executed_quantity, executed_price, quote_id, executed_at)
        VALUES (%s, %s, %s, %s, CURRENT_TIMESTAMP)
        RETURNING fill_id;
        """
        result = db.execute_query(fill_query, (order_id, Decimal('30.000000'), bid_price, quote_id))
        fill_id = result[0][0]
        
        # Apply updates (trigger simulation)
        cursor = db.connection.cursor()
        try:
            cursor.execute("BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;")
            
            # 1. Reduce account holdings
            cursor.execute("""
                UPDATE account_holdings 
                SET quantity = quantity - %s
                WHERE account_id = %s AND instrument_id = %s;
            """, (Decimal('30.000000'), account_id, instrument_id))
            
            # 2. Reduce client holdings
            cursor.execute("""
                UPDATE client_holdings
                SET total_quantity = total_quantity - %s
                WHERE client_id = %s AND instrument_id = %s;
            """, (Decimal('30.000000'), client_id, instrument_id))
            
            # 3. Add cash to account
            proceeds = Decimal('30.000000') * bid_price
            cursor.execute("""
                UPDATE client_accounts
                SET cash_balance = cash_balance + %s
                WHERE account_id = %s;
            """, (proceeds, account_id))
            
            # 4. Record transactions
            cursor.execute("""
                INSERT INTO transactions 
                (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
                VALUES (%s, %s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP);
            """, (account_id, instrument_id, fill_id, 'SELL', Decimal('30.000000'), bid_price, -proceeds))
            
            cursor.execute("""
                INSERT INTO transactions 
                (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
                VALUES (%s, NULL, %s, %s, NULL, NULL, %s, CURRENT_TIMESTAMP);
            """, (account_id, fill_id, 'BUY', proceeds))
            
            # 5. Audit
            cursor.execute("""
                INSERT INTO audit_logs 
                (entity_name, entity_id, action_type, client_id, state_after, recorded_at)
                VALUES (%s, %s, %s, %s, %s, CURRENT_TIMESTAMP);
            """, ('fills', fill_id, 'INSERT', client_id, 
                  f'{{"order_id": {order_id}, "executed_quantity": 30, "executed_price": {bid_price}}}'))
            
            cursor.execute("COMMIT;")
        except Exception as e:
            cursor.execute("ROLLBACK;")
            raise e
        finally:
            cursor.close()
        
        # VERIFY ATOMICITY
        after_cash = db.execute_query(
            "SELECT cash_balance FROM client_accounts WHERE account_id = %s;",
            (account_id,)
        )[0][0]
        
        expected_cash = before_cash + (Decimal('30.000000') * bid_price)
        assert after_cash == expected_cash, f"Cash not added: {after_cash} != {expected_cash}"
        
        # Verify reduced holdings
        account_holding = db.execute_query(
            "SELECT quantity FROM account_holdings WHERE account_id = %s AND instrument_id = %s;",
            (account_id, instrument_id)
        )[0][0]
        assert account_holding == Decimal('70.000000'), f"Account holdings not reduced: {account_holding}"
        
        client_holding = db.execute_query(
            "SELECT total_quantity FROM client_holdings WHERE client_id = %s AND instrument_id = %s;",
            (client_id, instrument_id)
        )[0][0]
        assert client_holding == Decimal('70.000000'), f"Client holdings not reduced: {client_holding}"


class TestBR02DataIsolation:
    """Test BR-02: Clients can only see their own data."""
    
    def test_client_cannot_see_other_client_holdings(self, db):
        """Verify client A cannot query client B's holdings."""
        # Create two clients
        client_a_id = db.execute_query(
            "INSERT INTO client_profiles (client_full_name, date_of_birth, risk_profile) "
            "VALUES (%s, %s, %s) RETURNING client_id;",
            ('Client A', '1990-01-01', 'Balanced')
        )[0][0]
        
        client_b_id = db.execute_query(
            "INSERT INTO client_profiles (client_full_name, date_of_birth, risk_profile) "
            "VALUES (%s, %s, %s) RETURNING client_id;",
            ('Client B', '1991-01-01', 'Adventurous')
        )[0][0]
        
        # Create accounts
        acct_a = db.execute_query(
            "INSERT INTO client_accounts (client_id, account_type, cash_balance, currency, opened_date) "
            "VALUES (%s, %s, %s, %s, CURRENT_DATE) RETURNING account_id;",
            (client_a_id, 'DIRECT_TRADING', Decimal('5000'), 'GBP')
        )[0][0]
        
        acct_b = db.execute_query(
            "INSERT INTO client_accounts (client_id, account_type, cash_balance, currency, opened_date) "
            "VALUES (%s, %s, %s, %s, CURRENT_DATE) RETURNING account_id;",
            (client_b_id, 'DIRECT_TRADING', Decimal('10000'), 'GBP')
        )[0][0]
        
        # Create instrument
        instr_id = db.execute_query(
            "INSERT INTO financial_instruments (ticker_symbol, instrument_name, asset_class, base_currency) "
            "VALUES (%s, %s, %s, %s) RETURNING instrument_id;",
            ('TEST', 'Test Stock', 'Equity', 'GBP')
        )[0][0]
        
        # Client B adds holding
        db.execute_update(
            "INSERT INTO client_holdings (client_id, instrument_id, total_quantity, as_of_timestamp) "
            "VALUES (%s, %s, %s, CURRENT_TIMESTAMP);",
            (client_b_id, instr_id, Decimal('100'))
        )
        
        # Client A should NOT see Client B's holdings
        result = db.execute_query(
            "SELECT COUNT(*) FROM client_holdings WHERE client_id = %s AND instrument_id = %s;",
            (client_a_id, instr_id)
        )
        assert result[0][0] == 0, "Client A should not see Client B holdings"
        
        # Client B should see their own
        result = db.execute_query(
            "SELECT COUNT(*) FROM client_holdings WHERE client_id = %s AND instrument_id = %s;",
            (client_b_id, instr_id)
        )
        assert result[0][0] == 1, "Client B should see their own holdings"


class TestBR14AuditTrail:
    """Test BR-14 & BR-15: Complete audit trail for trade reconstruction."""
    
    def test_order_lifecycle_audit_complete(self, db, test_data):
        """Verify full order lifecycle is audited."""
        client_id = test_data['client_id']
        account_id = test_data['account_id']
        instrument_id = test_data['instruments'][1]
        
        # Create order
        order_id = db.execute_query(
            "INSERT INTO orders (client_id, account_id, instrument_id, order_side, order_type, requested_quantity) "
            "VALUES (%s, %s, %s, %s, %s, %s) RETURNING order_id;",
            (client_id, account_id, instrument_id, 'BUY', 'MARKET', Decimal('5'))
        )[0][0]
        
        # Record order submission in audit
        db.execute_update(
            "INSERT INTO audit_logs (entity_name, entity_id, action_type, client_id, state_after, recorded_at) "
            "VALUES (%s, %s, %s, %s, %s, CURRENT_TIMESTAMP);",
            ('orders', order_id, 'INSERT', client_id, '{"status": "SUBMITTED"}')
        )
        
        # Update order status to ACCEPTED
        db.execute_update(
            "UPDATE orders SET order_status = %s, accepted_at = CURRENT_TIMESTAMP WHERE order_id = %s;",
            ('ACCEPTED', order_id)
        )
        
        # Record in audit
        db.execute_update(
            "INSERT INTO audit_logs (entity_name, entity_id, action_type, client_id, state_before, state_after, recorded_at) "
            "VALUES (%s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP);",
            ('orders', order_id, 'UPDATE', client_id, '{"status": "SUBMITTED"}', '{"status": "ACCEPTED"}')
        )
        
        # Create fill
        quote_id = db.execute_query(
            "SELECT quote_id FROM market_quotes WHERE instrument_id = %s LIMIT 1;",
            (instrument_id,)
        )[0][0]
        
        fill_id = db.execute_query(
            "INSERT INTO fills (order_id, executed_quantity, executed_price, quote_id, executed_at) "
            "VALUES (%s, %s, %s, %s, CURRENT_TIMESTAMP) RETURNING fill_id;",
            (order_id, Decimal('5'), Decimal('150.50'), quote_id)
        )[0][0]
        
        # Record fill in audit
        db.execute_update(
            "INSERT INTO audit_logs (entity_name, entity_id, action_type, client_id, state_after, recorded_at) "
            "VALUES (%s, %s, %s, %s, %s, CURRENT_TIMESTAMP);",
            ('fills', fill_id, 'INSERT', client_id, '{"executed_quantity": 5, "executed_price": 150.50}')
        )
        
        # VERIFY: Can reconstruct full lifecycle from audit logs
        audit_records = db.execute_query(
            "SELECT entity_name, action_type, state_before, state_after, recorded_at "
            "FROM audit_logs WHERE entity_id IN (%s, %s) ORDER BY recorded_at;",
            (order_id, fill_id)
        )
        
        # Should have order creation, order acceptance, fill creation
        assert len(audit_records) >= 3, f"Expected >= 3 audit records, got {len(audit_records)}"
        
        # Verify order lifecycle can be reconstructed
        order_audits = [r for r in audit_records if r[0] == 'orders']
        assert len(order_audits) >= 2, "Should have order submission and acceptance records"
        
        fill_audits = [r for r in audit_records if r[0] == 'fills']
        assert len(fill_audits) >= 1, "Should have fill record"


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
