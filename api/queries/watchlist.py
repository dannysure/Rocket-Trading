"""
Query functions for watchlist domain
"""
from sqlalchemy.orm import Session
from sqlalchemy import text
from decimal import Decimal
from datetime import datetime
from ..models import WatchlistItemResponse, WatchlistResponse
from typing import List

def get_watchlist(db: Session, client_id: int) -> WatchlistResponse:
    """
    ANALYTICS SECTION 12: Watchlist & Engagement
    Get client watchlist with alert prices and current quotes
    """
    query = text("""
        SELECT 
            w.instrument_id,
            fi.instrument_name,
            mq.mid_price,
            w.alert_price_buy,
            w.alert_price_sell,
            w.created_at
        FROM watchlist w
        JOIN financial_instruments fi ON w.instrument_id = fi.instrument_id
        LEFT JOIN market_quotes mq ON w.instrument_id = mq.instrument_id
        WHERE w.client_id = :client_id
        ORDER BY w.created_at DESC
    """)
    
    results = db.execute(query, {"client_id": client_id}).fetchall()
    
    items = [
        WatchlistItemResponse(
            instrument_id=row[0],
            instrument_name=row[1],
            current_price=Decimal(row[2]) if row[2] else Decimal(0),
            alert_price_buy=Decimal(row[3]) if row[3] else None,
            alert_price_sell=Decimal(row[4]) if row[4] else None,
            added_at=row[5]
        )
        for row in results
    ]
    
    return WatchlistResponse(
        client_id=client_id,
        items=items
    )

def get_most_watched_instruments(db: Session, limit: int = 20) -> List[dict]:
    """
    ANALYTICS SECTION 12b: Most watched instruments
    Track engagement via watchlist popularity
    """
    query = text("""
        SELECT 
            fi.instrument_id,
            fi.instrument_name,
            fi.asset_class,
            COUNT(w.client_id) as watch_count,
            mq.mid_price
        FROM financial_instruments fi
        LEFT JOIN watchlist w ON fi.instrument_id = w.instrument_id
        LEFT JOIN market_quotes mq ON fi.instrument_id = mq.instrument_id
        GROUP BY fi.instrument_id, fi.instrument_name, fi.asset_class, mq.mid_price
        ORDER BY watch_count DESC
        LIMIT :limit
    """)
    
    results = db.execute(query, {"limit": limit}).fetchall()
    
    return [
        {
            "instrument_id": row[0],
            "instrument_name": row[1],
            "asset_class": row[2],
            "watch_count": row[3],
            "current_price": float(Decimal(row[4])) if row[4] else 0.0
        }
        for row in results
    ]
