"""
FastAPI routes for perpetual futures market data and order entry.
"""
from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import (
    DirectTradingAccountResponse,
    PerpetualMarketOverviewResponse,
    PerpetualOrderSubmissionResponse,
    SubmitPerpetualOrderRequest,
)
from ..queries import perpetual_futures

router = APIRouter(prefix="/api/perpetual-futures", tags=["perpetual-futures"])


@router.get("/accounts", response_model=list[DirectTradingAccountResponse])
def get_accounts(db: Session = Depends(get_db)):
    try:
        return perpetual_futures.get_available_accounts(db)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Database error: {str(exc)}")


@router.get("/markets/{market_symbol}", response_model=PerpetualMarketOverviewResponse)
def get_market(
    market_symbol: str = Path(..., description="Perpetual market symbol"),
    client_id: int = Query(..., gt=0),
    account_id: int = Query(..., gt=0),
    db: Session = Depends(get_db),
):
    try:
        return perpetual_futures.get_market_overview(db, market_symbol, client_id, account_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Database error: {str(exc)}")


@router.post("/orders", response_model=PerpetualOrderSubmissionResponse)
def place_order(request: SubmitPerpetualOrderRequest, db: Session = Depends(get_db)):
    try:
        return perpetual_futures.submit_order(db, request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except Exception as exc:
        db.rollback()
        raise HTTPException(status_code=500, detail=f"Database error: {str(exc)}")
