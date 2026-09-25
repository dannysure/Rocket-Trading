"""
FastAPI routes for trading endpoints
GET /api/trading/orders/{client_id}
GET /api/trading/activity
GET /api/trading/popular-instruments
"""
from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import TradingActivityResponse
from ..queries import trading

router = APIRouter(prefix="/api/trading", tags=["trading"])

@router.get("/orders/{client_id}", response_model=list)
def get_orders(
    client_id: int = Path(..., gt=0, description="Client ID"),
    limit: int = Query(100, ge=1, le=1000, description="Max orders to return"),
    status: str = Query(None, description="Filter by order status (SUBMITTED, FILLED, REJECTED, etc)"),
    db: Session = Depends(get_db)
):
    """Get order history for a client with fills"""
    try:
        return trading.get_order_history(db, client_id, limit, status)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/activity", response_model=TradingActivityResponse)
def get_activity(
    client_id: int = Query(None, description="Optional client ID for per-client metrics"),
    db: Session = Depends(get_db)
):
    """Get trading activity metrics (orders, fills, fill rate)"""
    try:
        return trading.get_trading_activity(db, client_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/popular-instruments", response_model=list)
def get_popular(
    limit: int = Query(10, ge=1, le=100, description="Max instruments to return"),
    db: Session = Depends(get_db)
):
    """Get most popular instruments by order count (last 30 days)"""
    try:
        return trading.get_most_popular_instruments(db, limit)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
