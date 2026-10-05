"""
FastAPI routes for trading endpoints
GET /api/trading/orders/{client_id}
GET /api/trading/activity
GET /api/trading/popular-instruments
"""
from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.orm import Session
from ..database import get_db
from ..cache import get_cached_value, set_cached_value
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
    """Get order history for a client with fills (cached for 2 minutes)"""
    try:
        cache_key = f"trading:orders:{client_id}:{limit}:{status}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return cached
        
        # Cache miss
        result = trading.get_order_history(db, client_id, limit, status)
        
        # Store in cache (shorter TTL for order data - frequently changes)
        set_cached_value(cache_key, result, ttl_seconds=120)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/activity", response_model=TradingActivityResponse)
def get_activity(
    client_id: int = Query(None, description="Optional client ID for per-client metrics"),
    db: Session = Depends(get_db)
):
    """Get trading activity metrics (orders, fills, fill rate) (cached for 5 minutes)"""
    try:
        cache_key = f"trading:activity:{client_id if client_id else 'all'}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return TradingActivityResponse(**cached)
        
        # Cache miss
        result = trading.get_trading_activity(db, client_id)
        
        # Store in cache
        set_cached_value(cache_key, result.dict(), ttl_seconds=300)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/popular-instruments", response_model=list)
def get_popular(
    limit: int = Query(10, ge=1, le=100, description="Max instruments to return"),
    db: Session = Depends(get_db)
):
    """Get most popular instruments by order count (last 30 days) (cached for 1 hour)"""
    try:
        cache_key = f"trading:popular:{limit}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return cached
        
        # Cache miss
        result = trading.get_most_popular_instruments(db, limit)
        
        # Store in cache (longer TTL - rarely changes)
        set_cached_value(cache_key, result, ttl_seconds=3600)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
