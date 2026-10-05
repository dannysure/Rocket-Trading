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
from ..cache import get_cached_value, set_cached_value
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
    """Get 24-hour KPI metrics (orders, fills, fill rate, shares traded) (cached for 5 minutes)"""
    try:
        cache_key = f"analytics:kpis:{client_id if client_id else 'all'}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return KPIPanelResponse(**cached)
        
        # Cache miss
        result = analytics.get_kpi_panel(db, client_id)
        
        # Store in cache
        set_cached_value(cache_key, result.dict(), ttl_seconds=300)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/alerts", response_model=list)
def get_alerts(
    client_id: int = Query(None, description="Optional client ID to filter alerts"),
    severity: str = Query(None, description="Filter by severity (Critical, High, Medium, Low)"),
    db: Session = Depends(get_db)
):
    """Get risk alerts (concentration, large losses, etc) (cached for 2 minutes)"""
    try:
        cache_key = f"analytics:alerts:{client_id if client_id else 'all'}:{severity if severity else 'all'}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return cached
        
        # Cache miss
        result = analytics.get_risk_alerts(db, client_id, severity)
        
        # Store in cache (shorter TTL - alerts change frequently)
        set_cached_value(cache_key, result, ttl_seconds=120)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/audit-trail", response_model=list)
def get_audit(
    entity_name: str = Query(None, description="Filter by entity type (orders, fills, holdings, etc)"),
    entity_id: int = Query(None, description="Filter by entity ID"),
    limit: int = Query(100, ge=1, le=1000, description="Max entries to return"),
    db: Session = Depends(get_db)
):
    """Get audit trail with complete state before/after for compliance (cached for 1 hour)"""
    try:
        cache_key = f"analytics:audit:{entity_name}:{entity_id}:{limit}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return cached
        
        # Cache miss
        result = analytics.get_audit_trail(db, entity_name, entity_id, limit)
        
        # Store in cache (longer TTL - audit trail rarely changes)
        set_cached_value(cache_key, result, ttl_seconds=3600)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/order-lifecycle/{order_id}", response_model=OrderLifecycleResponse)
def get_order_lifecycle(
    order_id: int = Path(..., gt=0, description="Order ID"),
    db: Session = Depends(get_db)
):
    """Get complete order lifecycle from submission to execution with audit trail (cached for 10 minutes)"""
    try:
        cache_key = f"analytics:order-lifecycle:{order_id}"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return OrderLifecycleResponse(**cached)
        
        # Cache miss
        result = analytics.get_order_lifecycle(db, order_id)
        
        # Store in cache
        set_cached_value(cache_key, result.dict(), ttl_seconds=600)
        
        return result
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")

@router.get("/platform-overview", response_model=PlatformOverviewResponse)
def get_overview(db: Session = Depends(get_db)):
    """Get global platform statistics (clients, accounts, orders, fills, etc) (cached for 1 hour)"""
    try:
        cache_key = "analytics:platform-overview"
        
        # Try cache first
        cached = get_cached_value(cache_key)
        if cached:
            return PlatformOverviewResponse(**cached)
        
        # Cache miss
        result = analytics.get_platform_overview(db)
        
        # Store in cache (longer TTL - global stats change slowly)
        set_cached_value(cache_key, result.dict(), ttl_seconds=3600)
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
