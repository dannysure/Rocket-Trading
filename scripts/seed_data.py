#!/usr/bin/env python3
"""
Seed realistic sample data for analytics and testing
Populates: clients, accounts, instruments, quotes, orders, fills
"""

import psycopg2
from decimal import Decimal
from datetime import datetime, timedelta
import os
import random

def seed_data():
    """Populate database with realistic sample data"""
    
    # Connect to database
    conn = psycopg2.connect(
        host=os.getenv('DB_HOST', 'localhost'),
        port=int(os.getenv('DB_PORT', '5432')),
        database=os.getenv('DB_NAME', 'team_rocket_db'),
        user=os.getenv('DB_USER', 'postgres'),
        password=os.getenv('DB_PASSWORD', '')
    )
    cursor = conn.cursor()
    
    try:
        # Clean up existing data
        print("🧹 Cleaning up existing test data...")
        cursor.execute("DELETE FROM transactions")
        cursor.execute("DELETE FROM audit_logs")
        cursor.execute("DELETE FROM order_notifications")
        cursor.execute("DELETE FROM fills")
        cursor.execute("DELETE FROM orders")
        cursor.execute("DELETE FROM account_holdings")
        cursor.execute("DELETE FROM client_holdings")
        cursor.execute("DELETE FROM market_quotes")
        cursor.execute("DELETE FROM client_accounts")
        cursor.execute("DELETE FROM watchlist")
        cursor.execute("DELETE FROM client_subscriptions")
        cursor.execute("DELETE FROM client_profiles WHERE client_id > 0")
        cursor.execute("DELETE FROM financial_instruments WHERE instrument_id > 0")
        cursor.execute("DELETE FROM financial_advisors WHERE advisor_id > 0")
        conn.commit()
        
        # Create financial advisors
        print("📊 Creating financial advisors...")
        cursor.execute("""
            INSERT INTO financial_advisors (advisor_full_name, regional_office, hired_date)
            VALUES 
                ('Alice Johnson', 'London', '2012-01-15'),
                ('Bob Smith', 'Edinburgh', '2016-05-20'),
                ('Carol White', 'Manchester', '2008-11-10')
        """)
        conn.commit()
        
        # Create clients
        print("👥 Creating 10 clients...")
        clients = [
            ('Emma Thompson', '1985-03-15', 'Cautious'),
            ('James Mitchell', '1978-07-22', 'Balanced'),
            ('Sarah Davies', '1992-11-08', 'Adventurous'),
            ('Michael Brown', '1980-05-19', 'Balanced'),
            ('Lisa Anderson', '1988-09-30', 'Cautious'),
            ('David Wilson', '1975-01-12', 'Adventurous'),
            ('Jessica Lee', '1990-06-25', 'Balanced'),
            ('Robert Taylor', '1982-12-03', 'Balanced'),
            ('Catherine Hall', '1987-04-17', 'Cautious'),
            ('Edward Martin', '1979-08-29', 'Balanced'),
        ]
        
        client_ids = []
        for name, dob, risk in clients:
            cursor.execute("""
                INSERT INTO client_profiles (client_full_name, date_of_birth, risk_profile)
                VALUES (%s, %s, %s)
                RETURNING client_id
            """, (name, dob, risk))
            client_ids.append(cursor.fetchone()[0])
        
        conn.commit()
        
        # Create accounts for each client
        print("🏦 Creating accounts...")
        account_ids = []
        account_types = ['ISA', 'GIA', 'SIPP', 'DIRECT_TRADING']
        
        for client_id in client_ids:
            account_type = random.choice(account_types)
            cash_balance = Decimal('50000000.0000')  # 50M per account - enough for all trades
            cursor.execute("""
                INSERT INTO client_accounts (client_id, account_type, cash_balance, currency, opened_date)
                VALUES (%s, %s, %s, 'GBP', CURRENT_DATE)
                RETURNING account_id
            """, (client_id, account_type, cash_balance))
            account_ids.append(cursor.fetchone()[0])
        
        conn.commit()
        
        # Create financial instruments
        print("📈 Creating 12 financial instruments...")
        instruments = [
            ('AAPL', 'Apple Inc.', 'Equity', 'USD'),
            ('MSFT', 'Microsoft Corp', 'Equity', 'USD'),
            ('GOOGL', 'Alphabet Inc.', 'Equity', 'USD'),
            ('TSLA', 'Tesla Inc.', 'Equity', 'USD'),
            ('AMZN', 'Amazon.com Inc.', 'Equity', 'USD'),
            ('VOW3', 'Volkswagen AG', 'Equity', 'EUR'),
            ('SAP', 'SAP SE', 'Equity', 'EUR'),
            ('ASML', 'ASML Holding', 'Equity', 'EUR'),
            ('EURUSD', 'EUR/USD', 'FX', 'EUR'),
            ('GBPUSD', 'GBP/USD', 'FX', 'GBP'),
            ('BTCUSD', 'Bitcoin/USD', 'Crypto', 'USD'),
            ('ETHUSD', 'Ethereum/USD', 'Crypto', 'USD'),
        ]
        
        instrument_ids = []
        for ticker, name, asset_class, currency in instruments:
            cursor.execute("""
                INSERT INTO financial_instruments (ticker_symbol, instrument_name, asset_class, base_currency)
                VALUES (%s, %s, %s, %s)
                RETURNING instrument_id
            """, (ticker, name, asset_class, currency))
            instrument_ids.append(cursor.fetchone()[0])
        
        conn.commit()
        
        # Create market quotes
        print("💹 Creating market quotes...")
        prices = {
            0: Decimal('175.50'),   # AAPL
            1: Decimal('378.85'),   # MSFT
            2: Decimal('140.20'),   # GOOGL
            3: Decimal('242.15'),   # TSLA
            4: Decimal('178.90'),   # AMZN
            5: Decimal('185.30'),   # VOW3
            6: Decimal('92.45'),    # SAP
            7: Decimal('620.75'),   # ASML
            8: Decimal('1.0850'),   # EURUSD
            9: Decimal('1.2750'),   # GBPUSD
            10: Decimal('45820.00'), # BTCUSD
            11: Decimal('2950.00'),  # ETHUSD
        }
        
        quote_ids = []
        for i, instrument_id in enumerate(instrument_ids):
            bid = prices[i] * Decimal('0.995')
            ask = prices[i] * Decimal('1.005')
            cursor.execute("""
                INSERT INTO market_quotes (instrument_id, bid_price, ask_price)
                VALUES (%s, %s, %s)
                RETURNING quote_id
            """, (instrument_id, bid, ask))
            quote_ids.append(cursor.fetchone()[0])
        
        conn.commit()
        
        # Create orders and fills
        print("📋 Creating 100+ orders and fills...")
        order_count = 0
        fill_count = 0
        
        # Generate orders for random clients on random instruments
        for day_offset in range(14):  # Last 2 weeks
            for _ in range(random.randint(3, 5)):  # 3-5 orders per day
                client_idx = random.randint(0, len(client_ids) - 1)
                client_id = client_ids[client_idx]
                account_id = account_ids[client_idx]
                
                instrument_idx = random.randint(0, len(instrument_ids) - 1)
                instrument_id = instrument_ids[instrument_idx]
                quote_id = quote_ids[instrument_idx]
                
                # Only BUY orders to avoid needing existing holdings for SELL
                order_side = 'BUY'
                order_type = random.choice(['MARKET', 'LIMIT'])
                quantity = Decimal(random.randint(1, 5))  # Small quantities: 1-5 shares
                
                # Create order
                order_timestamp = datetime.now() - timedelta(days=day_offset)
                cursor.execute("""
                    INSERT INTO orders 
                    (client_id, account_id, instrument_id, order_side, order_type, requested_quantity, submitted_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    RETURNING order_id
                """, (client_id, account_id, instrument_id, order_side, order_type, quantity, order_timestamp))
                order_id = cursor.fetchone()[0]
                order_count += 1
                
                # Get current price and create fill (80% of orders get filled)
                if random.random() < 0.7:
                    cursor.execute("""
                        SELECT ask_price FROM market_quotes WHERE quote_id = %s
                    """, (quote_id,))
                    price = cursor.fetchone()[0]
                    
                    # BUY at slight premium to ask price
                    executed_price = price * Decimal(random.uniform(1.00, 1.02))
                    
                    fill_timestamp = order_timestamp + timedelta(minutes=random.randint(1, 120))
                    
                    cursor.execute("""
                        INSERT INTO fills 
                        (order_id, executed_quantity, executed_price, quote_id, executed_at)
                        VALUES (%s, %s, %s, %s, %s)
                        RETURNING fill_id
                    """, (order_id, quantity, executed_price, quote_id, fill_timestamp))
                    fill_count += 1
        
        conn.commit()
        
        # Create some watchlist entries
        print("⭐ Creating watchlist entries...")
        for client_idx in range(min(5, len(client_ids))):
            client_id = client_ids[client_idx]
            watched_instruments = random.sample(instrument_ids, k=random.randint(2, 5))
            for instrument_id in watched_instruments:
                cursor.execute("""
                    SELECT ask_price FROM market_quotes WHERE instrument_id = %s
                """, (instrument_id,))
                price = cursor.fetchone()[0]
                
                cursor.execute("""
                    INSERT INTO watchlist (client_id, instrument_id, alert_price_buy, alert_price_sell)
                    VALUES (%s, %s, %s, %s)
                """, (client_id, instrument_id, price * Decimal('0.95'), price * Decimal('1.05')))
        
        conn.commit()
        
        # Print summary
        print("\n✅ Sample Data Seeded Successfully!")
        print(f"   Clients: {len(client_ids)}")
        print(f"   Accounts: {len(account_ids)}")
        print(f"   Instruments: {len(instrument_ids)}")
        print(f"   Orders: {order_count}")
        print(f"   Fills: {fill_count}")
        print(f"\n🎯 Ready to run analytics notebook!")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()
        conn.close()

if __name__ == '__main__':
    seed_data()
