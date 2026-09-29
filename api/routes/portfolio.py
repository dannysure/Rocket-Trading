"""
FastAPI routes for portfolio endpoints
GET /api/portfolio/{client_id}
GET /api/portfolio/top-clients
GET /api/portfolio/risk/{client_id}
"""
from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.orm import Session
from ..database import get_db
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
    """Get portfolio performance for a client"""
    try:
        return portfolio.get_portfolio_performance(db, client_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/top-clients", response_model=list)
def get_top_clients(
    limit: int = Query(20, ge=1, le=100, description="Number of clients to return"),
    db: Session = Depends(get_db)
):
    """Get top clients by portfolio value"""
    try:
        return portfolio.get_top_clients_by_value(db, limit)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/risk/{client_id}", response_model=RiskProfileResponse)
def get_risk(
    client_id: int = Path(..., gt=0, description="Client ID"),
    db: Session = Depends(get_db)
):
    """Get risk profile for a client"""
    try:
        return portfolio.get_risk_profile(db, client_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
