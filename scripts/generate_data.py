"""
Simulated Data Generator for Rocket Trading Platform
Generates realistic test data for users, trades, portfolios, and market activity
"""

import os
import random
from datetime import datetime, timedelta
import psycopg2
from psycopg2.extras import execute_values
from dotenv import load_dotenv

load_dotenv()

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "5433"))
DB_NAME = os.getenv("DB_NAME", "team_rocket_db")
DB_USER = os.getenv("DB_USER", "team_rocket_admin")
DB_PASSWORD = os.getenv("DB_PASSWORD", "team_rocket_password123")

def get_connection():
    return psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        database=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD
    )

# Sample data
ADVISORS = [
    ("John Smith", "London"),
    ("Sarah Johnson", "Manchester"),
    ("Michael Chen", "Edinburgh"),
    ("Emma Brown", "Dublin"),
]

INSTRUMENTS = [
    ("AAPL", "Apple Inc.", "Equity", "USD"),
    ("MSFT", "Microsoft Corporation", "Equity", "USD"),
    ("GOOGL", "Alphabet Inc.", "Equity", "USD"),
    ("TSLA", "Tesla Inc.", "Equity", "USD"),
    ("AMZN", "Amazon.com Inc.", "Equity", "USD"),
    ("BRK.B", "Berkshire Hathaway", "Equity", "USD"),
    ("NVDA", "NVIDIA Corporation", "Equity", "USD"),
    ("JPM", "JPMorgan Chase", "Equity", "USD"),
    ("V", "Visa Inc.", "Equity", "USD"),
    ("WMT", "Walmart Inc.", "Equity", "USD"),
    ("VTSAX", "Vanguard Total US Stock", "Fund", "USD"),
    ("VTIAX", "Vanguard Total International Stock", "Fund", "USD"),
    ("BND", "Vanguard Total Bond Market", "Bond", "USD"),
    ("EURUSD", "EUR/USD", "FX", "EUR"),
    ("GBPUSD", "GBP/USD", "FX", "GBP"),
    ("BTC", "Bitcoin", "Crypto", "USD"),
    ("ETH", "Ethereum", "Crypto", "USD"),
]

ACCOUNT_TYPES = ["ISA", "GIA", "SIPP", "DIRECT_TRADING"]
RISK_PROFILES = ["Cautious", "Balanced", "Adventurous"]

def generate_advisors(conn):
    """Insert financial advisors"""
    cursor = conn.cursor()
    values = [(name, office) for name, office in ADVISORS]
    query = "INSERT INTO financial_advisors (advisor_full_name, regional_office, hired_date) VALUES %s RETURNING advisor_id"
    execute_values(cursor, query, [(v[0], v[1], datetime.now().date()) for v in values])
    conn.commit()
    print(f"✓ Generated {len(ADVISORS)} advisors")
    return [row[0] for row in cursor.fetchall()]

def generate_instruments(conn):
    """Insert financial instruments"""
    cursor = conn.cursor()
    query = "INSERT INTO financial_instruments (ticker_symbol, instrument_name, asset_class, base_currency, is_tradable) VALUES %s RETURNING instrument_id"
    instruments_with_tradable = [(t[0], t[1], t[2], t[3], True) for t in INSTRUMENTS]
    execute_values(cursor, query, instruments_with_tradable)
    conn.commit()
    print(f"✓ Generated {len(INSTRUMENTS)} instruments")
    return [row[0] for row in cursor.fetchall()]

def generate_clients(conn, advisor_ids, num_clients=50):
    """Generate client profiles"""
    cursor = conn.cursor()
    first_names = ["Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry"]
    last_names = ["Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Wilson"]
    
    clients = []
    for i in range(num_clients):
        first = random.choice(first_names)
        last = random.choice(last_names)
        dob = datetime(1960, 1, 1) + timedelta(days=random.randint(0, 20000))
        risk = random.choice(RISK_PROFILES)
        advisor_id = random.choice(advisor_ids) if random.random() > 0.3 else None
        clients.append((f"{first} {last}", dob.date(), risk, advisor_id))
    
    query = "INSERT INTO client_profiles (client_full_name, date_of_birth, risk_profile, advisor_id) VALUES %s RETURNING client_id"
    execute_values(cursor, query, clients)
    conn.commit()
    print(f"✓ Generated {num_clients} client profiles")
    return [row[0] for row in cursor.fetchall()]

def generate_accounts(conn, client_ids, accounts_per_client=2):
    """Generate client accounts (ISA, GIA, etc.)"""
    cursor = conn.cursor()
    accounts = []
    
    for client_id in client_ids:
        num_accounts = random.randint(1, accounts_per_client)
        for _ in range(num_accounts):
            account_type = random.choice(ACCOUNT_TYPES)
            cash = random.uniform(1000, 100000)
            opened = datetime.now().date() - timedelta(days=random.randint(30, 1000))
            accounts.append((client_id, account_type, cash, "GBP", opened))
    
    query = "INSERT INTO client_accounts (client_id, account_type, cash_balance, currency, opened_date) VALUES %s RETURNING account_id"
    execute_values(cursor, query, accounts)
    conn.commit()
    print(f"✓ Generated {len(accounts)} client accounts")
    return [row[0] for row in cursor.fetchall()]

def generate_holdings(conn, account_ids, instrument_ids):
    """Generate account holdings"""
    cursor = conn.cursor()
    holdings = []
    
    for account_id in account_ids:
        num_holdings = random.randint(1, 8)
        selected_instruments = random.sample(instrument_ids, min(num_holdings, len(instrument_ids)))
        for instrument_id in selected_instruments:
            quantity = random.uniform(1, 500)
            holdings.append((account_id, instrument_id, quantity))
    
    query = "INSERT INTO account_holdings (account_id, instrument_id, quantity) VALUES %s"
    execute_values(cursor, query, holdings)
    conn.commit()
    print(f"✓ Generated {len(holdings)} account holdings")

def generate_market_quotes(conn, instrument_ids, quotes_per_instrument=5):
    """Generate market price quotes"""
    cursor = conn.cursor()
    quotes = []
    
    base_prices = {
        1: 150,  # AAPL
        2: 380,  # MSFT
        3: 140,  # GOOGL
        4: 250,  # TSLA
        5: 180,  # AMZN
        6: 600,  # BRK.B
        7: 880,  # NVDA
        8: 190,  # JPM
        9: 280,  # V
        10: 85,  # WMT
        11: 90,  # VTSAX
        12: 75,  # VTIAX
        13: 82,  # BND
    }
    
    for instrument_id in instrument_ids[:10]:  # Just equities and funds
        base_price = base_prices.get(instrument_id, 100)
        for _ in range(quotes_per_instrument):
            # Simulate bid-ask spread
            bid = base_price + random.uniform(-5, 5)
            ask = bid + random.uniform(0.1, 2)
            timestamp = datetime.now() - timedelta(minutes=random.randint(0, 1440))
            quotes.append((instrument_id, bid, ask, timestamp))
    
    query = "INSERT INTO market_quotes (instrument_id, bid_price, ask_price, quote_timestamp) VALUES %s"
    execute_values(cursor, query, quotes)
    conn.commit()
    print(f"✓ Generated {len(quotes)} market quotes")

def generate_orders_and_fills(conn, client_ids, account_ids, instrument_ids, num_orders=100):
    """Generate orders and fills (trades)"""
    cursor = conn.cursor()
    
    orders = []
    fills = []
    transactions = []
    
    for _ in range(num_orders):
        client_id = random.choice(client_ids)
        account_id = random.choice(account_ids)
        instrument_id = random.choice(instrument_ids[:10])  # Stick to equities/funds
        side = random.choice(["BUY", "SELL"])
        order_type = random.choice(["MARKET", "LIMIT"])
        quantity = random.uniform(1, 100)
        limit_price = random.uniform(50, 500) if order_type == "LIMIT" else None
        
        orders.append((
            client_id, account_id, instrument_id,
            side, order_type, quantity, limit_price,
            "FILLED", None  # status, rejection_reason
        ))
    
    # Insert orders
    query = """
    INSERT INTO orders 
    (client_id, account_id, instrument_id, order_side, order_type, 
     requested_quantity, limit_price, order_status, rejection_reason)
    VALUES %s RETURNING order_id
    """
    execute_values(cursor, query, orders)
    order_ids = [row[0] for row in cursor.fetchall()]
    conn.commit()
    
    # Generate fills for orders
    for order_id in order_ids:
        executed_qty = random.uniform(0.5, 1.0) * 100  # Partial or full fill
        executed_price = random.uniform(50, 500)
        timestamp = datetime.now() - timedelta(days=random.randint(0, 30))
        fills.append((order_id, executed_qty, executed_price, None, timestamp))
    
    query = """
    INSERT INTO fills (order_id, executed_quantity, executed_price, quote_id, executed_at)
    VALUES %s RETURNING fill_id
    """
    execute_values(cursor, query, fills)
    fill_ids = [row[0] for row in cursor.fetchall()]
    conn.commit()
    
    # Generate transactions from fills
    for i, fill_id in enumerate(fill_ids):
        account_id = random.choice(account_ids)
        instrument_id = random.choice(instrument_ids[:10])
        tx_type = random.choice(["BUY", "SELL"])
        quantity = random.uniform(0.5, 100)
        unit_price = random.uniform(50, 500)
        net_amount = quantity * unit_price if tx_type == "BUY" else -(quantity * unit_price)
        tx_date = datetime.now() - timedelta(days=random.randint(0, 30))
        
        transactions.append((
            account_id, instrument_id, fill_id,
            tx_type, quantity, unit_price, net_amount, tx_date
        ))
    
    query = """
    INSERT INTO transactions 
    (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
    VALUES %s
    """
    execute_values(cursor, query, transactions)
    conn.commit()
    
    print(f"✓ Generated {len(orders)} orders, {len(fills)} fills, {len(transactions)} transactions")

def generate_audit_logs(conn, num_logs=50):
    """Generate audit logs for compliance"""
    cursor = conn.cursor()
    audit_logs = []
    
    entity_names = ["orders", "fills", "transactions", "accounts", "holdings"]
    action_types = ["INSERT", "UPDATE", "DELETE"]
    
    for _ in range(num_logs):
        entity = random.choice(entity_names)
        entity_id = random.randint(1, 1000)
        action = random.choice(action_types)
        client_id = random.randint(1, 50) if random.random() > 0.3 else None
        timestamp = datetime.now() - timedelta(days=random.randint(0, 30))
        
        audit_logs.append((
            entity, entity_id, action, client_id,
            '{"old": "value"}', '{"new": "value"}', timestamp
        ))
    
    query = """
    INSERT INTO audit_logs 
    (entity_name, entity_id, action_type, client_id, state_before, state_after, recorded_at)
    VALUES %s
    """
    execute_values(cursor, query, audit_logs)
    conn.commit()
    print(f"✓ Generated {len(audit_logs)} audit logs")

def main():
    """Main data generation workflow"""
    conn = get_connection()
    
    try:
        print("🚀 Starting data generation...\n")
        
        # Generate base data
        advisor_ids = generate_advisors(conn)
        instrument_ids = generate_instruments(conn)
        client_ids = generate_clients(conn, advisor_ids, num_clients=50)
        account_ids = generate_accounts(conn, client_ids)
        
        # Generate trading activity
        generate_holdings(conn, account_ids, instrument_ids)
        generate_market_quotes(conn, instrument_ids)
        generate_orders_and_fills(conn, client_ids, account_ids, instrument_ids, num_orders=100)
        generate_audit_logs(conn)
        
        print("\n✅ Data generation complete!")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        conn.rollback()
    finally:
        conn.close()

if __name__ == "__main__":
    main()
