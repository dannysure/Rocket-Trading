"""
FastAPI routes for portfolio endpoints
GET /api/portfolio/{client_id}
GET /api/portfolio/top-clients
GET /api/portfolio/risk/{client_id}
"""
from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.orm import Session
from ..database import get_db
from ..cache import get_cached_value, set_cached_value
from ..models import (
    PortfolioPerformanceResponse, RiskProfileResponse
)
from ..queries import portfolio

router = APIRouter(prefix="/api/portfolio", tags=["portfolio"])

@router.get("/{client_id}", response_model=PortfolioPerformanceResponse)
def get_portfolio(
    client_id: int = Path(..., gt=0, description="Client ID"),
    db: Session = Depends(get_db)
):
    """Get portfolio performance for a client (cached for 5 minutes)"""
    try:
        cache_key = f"portfolio:{client_id}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return PortfolioPerformanceResponse(**cached)
        
        # Cache miss - fetch from DB
        result = portfolio.get_portfolio_performance(db, client_id)
        
        # Store in cache
        set_cached_value(cache_key, result.dict(), ttl_seconds=300)
        
        return result
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/top-clients", response_model=list)
def get_top_clients(
    limit: int = Query(20, ge=1, le=100, description="Number of clients to return"),
    db: Session = Depends(get_db)
):
    """Get top clients by portfolio value (cached for 10 minutes)"""
    try:
        cache_key = f"top-clients:{limit}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return cached
        
        # Cache miss - fetch from DB
        result = portfolio.get_top_clients_by_value(db, limit)
        
        # Store in cache
        set_cached_value(cache_key, result, ttl_seconds=600)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/risk/{client_id}", response_model=RiskProfileResponse)
def get_risk(
    client_id: int = Path(..., gt=0, description="Client ID"),
    db: Session = Depends(get_db)
):
    """Get risk profile for a client (cached for 5 minutes)"""
    try:
        cache_key = f"risk:{client_id}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return RiskProfileResponse(**cached)
        
        # Cache miss - fetch from DB
        result = portfolio.get_risk_profile(db, client_id)
        
        # Store in cache
        set_cached_value(cache_key, result.dict(), ttl_seconds=300)
        
        return result
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
