"""
Query functions for portfolio analytics
Wraps SQL queries into Python functions returning structured data
"""
from sqlalchemy.orm import Session
from sqlalchemy import text
from decimal import Decimal
from ..models import (
    PortfolioPerformanceResponse, HoldingResponse, RiskProfileResponse
)
from typing import List

def get_portfolio_performance(db: Session, client_id: int) -> PortfolioPerformanceResponse:
    """
    ANALYTICS SECTION 1: Portfolio Performance
    Returns top holdings, cash balance, and total portfolio value
    """
    query = text("""
        SELECT 
            ca.cash_balance,
            SUM(ch.quantity * mq.mid_price) as total_holdings_value
        FROM client_accounts ca
        LEFT JOIN client_holdings ch ON ca.account_id = ch.account_id
        LEFT JOIN market_quotes mq ON ch.instrument_id = mq.instrument_id
        WHERE ca.client_id = :client_id
        GROUP BY ca.cash_balance
    """)
    result = db.execute(query, {"client_id": client_id}).fetchone()
    
    if not result:
        raise ValueError(f"Client {client_id} not found")
    
    cash = Decimal(result[0]) if result[0] else Decimal(0)
    holdings_value = Decimal(result[1]) if result[1] else Decimal(0)
    
    # Get detailed holdings
    holdings_query = text("""
        SELECT 
            ch.instrument_id,
            fi.instrument_name,
            fi.asset_class,
            ch.quantity,
            mq.mid_price,
            ch.quantity * mq.mid_price as total_value
        FROM client_holdings ch
        JOIN financial_instruments fi ON ch.instrument_id = fi.instrument_id
        JOIN market_quotes mq ON ch.instrument_id = mq.instrument_id
        WHERE ch.client_id = :client_id
        ORDER BY total_value DESC
        LIMIT 20
    """)
    holdings_result = db.execute(holdings_query, {"client_id": client_id}).fetchall()
    
    holdings = [
        HoldingResponse(
            instrument_id=row[0],
            instrument_name=row[1],
            asset_class=row[2],
            quantity=row[3],
            current_price=Decimal(row[4]),
            total_value=Decimal(row[5])
        )
        for row in holdings_result
    ]
    
    return PortfolioPerformanceResponse(
        client_id=client_id,
        total_cash=cash,
        total_holdings_value=holdings_value,
        total_portfolio_value=cash + holdings_value,
        holdings=holdings
    )

def get_risk_profile(db: Session, client_id: int) -> RiskProfileResponse:
    """
    ANALYTICS SECTION 2: Risk Analytics
    Returns client risk profile and asset allocation breakdown
    """
    # Get risk level
    profile_query = text("""
        SELECT cp.risk_tolerance
        FROM client_profiles cp
        WHERE cp.client_id = :client_id
    """)
    profile_result = db.execute(profile_query, {"client_id": client_id}).fetchone()
    
    if not profile_result:
        raise ValueError(f"Client {client_id} not found")
    
    risk_level = profile_result[0] or "Unknown"
    
    # Get asset allocation
    allocation_query = text("""
        SELECT 
            fi.asset_class,
            SUM(ch.quantity * mq.mid_price) as total_value
        FROM client_holdings ch
        JOIN financial_instruments fi ON ch.instrument_id = fi.instrument_id
        JOIN market_quotes mq ON ch.instrument_id = mq.instrument_id
        WHERE ch.client_id = :client_id
        GROUP BY fi.asset_class
    """)
    allocation_result = db.execute(allocation_query, {"client_id": client_id}).fetchall()
    
    total_value = sum((Decimal(row[1]) if row[1] else Decimal(0) for row in allocation_result), Decimal(0))
    
    allocations = {row[0]: Decimal(row[1]) if row[1] else Decimal(0) for row in allocation_result}
    
    equity_pct = float((allocations.get("EQUITY", 0) / total_value * 100)) if total_value > 0 else 0
    fx_pct = float((allocations.get("FOREX", 0) / total_value * 100)) if total_value > 0 else 0
    crypto_pct = float((allocations.get("CRYPTO", 0) / total_value * 100)) if total_value > 0 else 0
    
    return RiskProfileResponse(
        client_id=client_id,
        risk_level=risk_level,
        equity_allocation_pct=equity_pct,
        fx_allocation_pct=fx_pct,
        crypto_allocation_pct=crypto_pct,
        total_value=total_value
    )

def get_top_clients_by_value(db: Session, limit: int = 20) -> List[dict]:
    """
    ANALYTICS SECTION 1b: Top 20 clients by portfolio value
    """
    query = text("""
        SELECT 
            cp.client_id,
            cp.client_name,
            ca.cash_balance,
            SUM(ch.quantity * mq.mid_price) as holdings_value,
            ca.cash_balance + SUM(ch.quantity * mq.mid_price) as total_value
        FROM client_profiles cp
        JOIN client_accounts ca ON cp.client_id = ca.client_id
        LEFT JOIN client_holdings ch ON ca.account_id = ch.account_id
        LEFT JOIN market_quotes mq ON ch.instrument_id = mq.instrument_id
        GROUP BY cp.client_id, cp.client_name, ca.cash_balance
        ORDER BY total_value DESC
        LIMIT :limit
    """)
    results = db.execute(query, {"limit": limit}).fetchall()
    
    return [
        {
            "client_id": row[0],
            "client_name": row[1],
            "cash_balance": float(Decimal(row[2])),
            "holdings_value": float(Decimal(row[3]) if row[3] else 0),
            "total_value": float(Decimal(row[4]) if row[4] else 0)
        }
        for row in results
    ]
