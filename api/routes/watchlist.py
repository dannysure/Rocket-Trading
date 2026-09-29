"""
FastAPI routes for watchlist endpoints
GET /api/watchlist/{client_id}
GET /api/watchlist/popular
"""
from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import WatchlistResponse
from ..queries import watchlist

router = APIRouter(prefix="/api/watchlist", tags=["watchlist"])

@router.get("/{client_id}", response_model=WatchlistResponse)
def get_watchlist(
    client_id: int = Path(..., gt=0, description="Client ID"),
    db: Session = Depends(get_db)
):
    """Get watchlist for a client with current quotes and alert prices"""
    try:
        return watchlist.get_watchlist(db, client_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/popular", response_model=list)
def get_popular(
    limit: int = Query(20, ge=1, le=100, description="Max instruments to return"),
    db: Session = Depends(get_db)
):
    """Get most watched instruments by watch count"""
    try:
        return watchlist.get_most_watched_instruments(db, limit)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
