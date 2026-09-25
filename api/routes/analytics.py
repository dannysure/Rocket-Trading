"""
FastAPI routes for analytics endpoints
GET /api/analytics/kpis
GET /api/analytics/alerts
GET /api/analytics/audit-trail
GET /api/analytics/order-lifecycle/{order_id}
GET /api/analytics/platform-overview
"""
from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import (
    KPIPanelResponse, PlatformOverviewResponse, OrderLifecycleResponse
)
from ..queries import analytics

router = APIRouter(prefix="/api/analytics", tags=["analytics"])

@router.get("/kpis", response_model=KPIPanelResponse)
def get_kpis(
    client_id: int = Query(None, description="Optional client ID for per-client KPIs"),
    db: Session = Depends(get_db)
):
    """Get 24-hour KPI metrics (orders, fills, fill rate, shares traded)"""
    try:
        return analytics.get_kpi_panel(db, client_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/alerts", response_model=list)
def get_alerts(
    client_id: int = Query(None, description="Optional client ID to filter alerts"),
    severity: str = Query(None, description="Filter by severity (Critical, High, Medium, Low)"),
    db: Session = Depends(get_db)
):
    """Get risk alerts (concentration, large losses, etc)"""
    try:
        return analytics.get_risk_alerts(db, client_id, severity)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/audit-trail", response_model=list)
def get_audit(
    entity_name: str = Query(None, description="Filter by entity type (orders, fills, holdings, etc)"),
    entity_id: int = Query(None, description="Filter by entity ID"),
    limit: int = Query(100, ge=1, le=1000, description="Max entries to return"),
    db: Session = Depends(get_db)
):
    """Get audit trail with complete state before/after for compliance"""
    try:
        return analytics.get_audit_trail(db, entity_name, entity_id, limit)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/order-lifecycle/{order_id}", response_model=OrderLifecycleResponse)
def get_order_lifecycle(
    order_id: int = Path(..., gt=0, description="Order ID"),
    db: Session = Depends(get_db)
):
    """Get complete order lifecycle from submission to execution with audit trail"""
    try:
        return analytics.get_order_lifecycle(db, order_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/platform-overview", response_model=PlatformOverviewResponse)
def get_overview(db: Session = Depends(get_db)):
    """Get global platform statistics (clients, accounts, orders, fills, etc)"""
    try:
        return analytics.get_platform_overview(db)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
