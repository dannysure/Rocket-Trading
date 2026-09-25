"""
Query functions for trading analytics
Wraps SQL queries for order and execution analysis
"""
from sqlalchemy.orm import Session
from sqlalchemy import text
from decimal import Decimal
from datetime import datetime
from ..models import (
    OrderResponse, FillResponse, TradingActivityResponse
)
from typing import List, Optional, Any, Dict

def get_order_history(
    db: Session, 
    client_id: int, 
    limit: int = 100,
    order_status: Optional[str] = None
) -> List[OrderResponse]:
    """
    ANALYTICS SECTION 4: Trading Activity - Get order history with fills
    Filters by client_id and optional order_status
    """
    base_query = """
        SELECT 
            o.order_id,
            fi.instrument_name,
            o.order_side,
            o.order_type,
            o.requested_quantity,
            o.order_status,
            o.created_at
        FROM orders o
        JOIN financial_instruments fi ON o.instrument_id = fi.instrument_id
        WHERE o.client_id = :client_id
    """
    
    params: Dict[str, Any] = {"client_id": client_id, "limit": limit}
    
    if order_status:
        base_query += " AND o.order_status = :order_status"
        params["order_status"] = order_status
    
    base_query += " ORDER BY o.created_at DESC LIMIT :limit"
    
    query = text(base_query)
    results = db.execute(query, params).fetchall() or []
    
    orders = []
    for row in results:
        order_id = row[0]
        
        # Get fills for this order
        fills_query = text("""
            SELECT fill_id, executed_quantity, executed_price, executed_at
            FROM fills
            WHERE order_id = :order_id
            ORDER BY executed_at
        """)
        fills_result = db.execute(fills_query, {"order_id": order_id}).fetchall()
        
        fills = [
            FillResponse(
                fill_id=fill_row[0],
                order_id=order_id,
                executed_quantity=fill_row[1],
                executed_price=Decimal(fill_row[2]),
                executed_at=fill_row[3]
            )
            for fill_row in fills_result
        ]
        
        orders.append(OrderResponse(
            order_id=order_id,
            instrument_name=row[1],
            order_side=row[2],
            order_type=row[3],
            requested_quantity=row[4],
            order_status=row[5],
            created_at=row[6],
            fills=fills
        ))
    
    return orders

def get_trading_activity(db: Session, client_id: Optional[int] = None) -> TradingActivityResponse:
    """
    ANALYTICS SECTION 4b: Trading Activity metrics
    Global or per-client order and fill statistics
    """
    where_clause = f"WHERE o.client_id = :client_id" if client_id else ""
    params: Dict[str, Any] = {"client_id": client_id} if client_id else {}
    
    query = text(f"""
        SELECT 
            COUNT(o.order_id) as total_orders,
            SUM(CASE WHEN o.order_side = 'BUY' THEN 1 ELSE 0 END) as buy_orders,
            SUM(CASE WHEN o.order_side = 'SELL' THEN 1 ELSE 0 END) as sell_orders,
            SUM(CASE WHEN o.order_status = 'FILLED' THEN 1 ELSE 0 END) as filled_orders,
            SUM(CASE WHEN o.order_status = 'REJECTED' THEN 1 ELSE 0 END) as rejected_orders,
            SUM(f.executed_quantity) as total_shares_traded
        FROM orders o
        LEFT JOIN fills f ON o.order_id = f.order_id
        {where_clause}
    """)
    
    result = db.execute(query, params).fetchone()
    
    if result is None:
        return TradingActivityResponse(
            total_orders=0,
            buy_orders=0,
            sell_orders=0,
            filled_orders=0,
            rejected_orders=0,
            fill_rate_pct=0.0,
            total_shares_traded=0
        )
    
    total_orders = result[0] or 0
    filled_orders = result[3] or 0
    
    return TradingActivityResponse(
        total_orders=total_orders,
        buy_orders=result[1] or 0,
        sell_orders=result[2] or 0,
        filled_orders=filled_orders,
        rejected_orders=result[4] or 0,
        fill_rate_pct=float((filled_orders / total_orders * 100)) if total_orders > 0 else 0.0,
        total_shares_traded=result[5] or 0
    )

def get_most_popular_instruments(db: Session, limit: int = 10) -> List[dict]:
    """
    ANALYTICS SECTION 3: Market Trends - Most popular instruments
    Orders in last 30 days
    """
    query = text("""
        SELECT 
            fi.instrument_id,
            fi.instrument_name,
            fi.asset_class,
            COUNT(o.order_id) as order_count,
            mq.mid_price
        FROM financial_instruments fi
        LEFT JOIN orders o ON fi.instrument_id = o.instrument_id 
            AND o.created_at >= NOW() - INTERVAL '30 days'
        LEFT JOIN market_quotes mq ON fi.instrument_id = mq.instrument_id
        GROUP BY fi.instrument_id, fi.instrument_name, fi.asset_class, mq.mid_price
        ORDER BY order_count DESC
        LIMIT :limit
    """)
    results = db.execute(query, {"limit": limit}).fetchall()
    
    return [
        {
            "instrument_id": row[0],
            "instrument_name": row[1],
            "asset_class": row[2],
            "popularity_score": row[3],
            "current_price": float(Decimal(row[4])) if row[4] else 0.0
        }
        for row in results
    ]
