"""
FastAPI routes for watchlist endpoints
GET /api/watchlist/{client_id}
GET /api/watchlist/popular
"""
from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.orm import Session
from ..database import get_db
from ..cache import get_cached_value, set_cached_value
from ..models import WatchlistResponse
from ..queries import watchlist

router = APIRouter(prefix="/api/watchlist", tags=["watchlist"])

@router.get("/{client_id}", response_model=WatchlistResponse)
def get_watchlist(
    client_id: int = Path(..., gt=0, description="Client ID"),
    db: Session = Depends(get_db)
):
    """Get watchlist for a client with current quotes and alert prices (cached for 2 minutes)"""
    try:
        cache_key = f"watchlist:{client_id}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return WatchlistResponse(**cached)
        
        # Cache miss
        result = watchlist.get_watchlist(db, client_id)
        
        # Store in cache (shorter TTL - prices update frequently)
        set_cached_value(cache_key, result.dict(), ttl_seconds=120)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/popular", response_model=list)
def get_popular(
    limit: int = Query(20, ge=1, le=100, description="Max instruments to return"),
    db: Session = Depends(get_db)
):
    """Get most watched instruments by watch count (cached for 1 hour)"""
    try:
        cache_key = f"watchlist:popular:{limit}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return cached
        
        # Cache miss
        result = watchlist.get_most_watched_instruments(db, limit)
        
        # Store in cache (longer TTL - watch count changes slowly)
        set_cached_value(cache_key, result, ttl_seconds=3600)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
