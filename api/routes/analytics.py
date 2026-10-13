"""
FastAPI routes for analytics endpoints
Redis-cached KPI endpoints
"""

import hmac
import os
from fastapi import APIRouter, Depends, Header, HTTPException, Query
from sqlalchemy.orm import Session
import redis
from datetime import datetime
from decimal import Decimal

from api.database import get_db
from api.cache_service import AnalyticsCacheService
from api.models import KPIPanelResponse

router = APIRouter(prefix="/api/analytics", tags=["analytics"])

# Global Redis connection (initialized in main.py)
_redis_client: redis.Redis = None


def require_cache_admin(x_admin_token: str | None = Header(default=None)) -> bool:
    configured_token = os.getenv('ANALYTICS_ADMIN_TOKEN')
    if not configured_token:
        raise HTTPException(status_code=503, detail="Cache administration is not configured")
    if not x_admin_token or not hmac.compare_digest(x_admin_token, configured_token):
        raise HTTPException(status_code=403, detail="Cache administration is forbidden")
    return True


def get_redis() -> redis.Redis:
    """Dependency to inject Redis client"""
    global _redis_client
    if _redis_client is None:
        import os
        redis_host = os.getenv('REDIS_HOST', 'localhost')
        redis_port = int(os.getenv('REDIS_PORT', 6379))
        _redis_client = redis.Redis(
            host=redis_host,
            port=redis_port,
            decode_responses=True
        )
    return _redis_client


@router.get("/kpis")
def get_kpis(
    period: str = Query("day", enum=["minute", "hour", "day"]),
    db: Session = Depends(get_db),
    redis_client: redis.Redis = Depends(get_redis)
):
    """
    Get 24-hour KPI metrics (fills, GMV, fill rate, top symbols/clients)
    Data is cached in Redis and updated by Kafka consumer
    """
    try:
        cache_service = AnalyticsCacheService(redis_client)
        kpis = cache_service.get_kpi_panel(time_period=period)
        return kpis
    except Exception as e:
        return {
            "error": str(e),
            "fills": 0,
            "gmv": 0,
            "fill_rate": 0
        }


@router.get("/platform-overview")
def get_overview(
    db: Session = Depends(get_db),
    redis_client: redis.Redis = Depends(get_redis)
):
    """Get global platform statistics"""
    try:
        cache_service = AnalyticsCacheService(redis_client)
        day_kpis = cache_service.get_kpi_panel(time_period="day")
        
        return {
            "total_clients": 0,  # TODO: Query from DB
            "total_accounts": 0,  # TODO: Query from DB
            "total_orders": 0,  # TODO: Query from DB
            "total_fills": day_kpis.get('fills', 0),
            "fill_rate": day_kpis.get('fill_rate', 0),
            "total_gmv": day_kpis.get('gmv', 0),
            "top_symbols": day_kpis.get('top_symbols', []),
            "top_clients": day_kpis.get('top_clients', []),
            "cached_at": datetime.utcnow().isoformat()
        }
    except Exception as e:
        return {"error": str(e)}


@router.get("/cache-stats")
def get_cache_stats(redis_client: redis.Redis = Depends(get_redis)):
    """Get Redis cache statistics"""
    try:
        cache_service = AnalyticsCacheService(redis_client)
        return cache_service.get_cache_stats()
    except Exception as e:
        return {"error": str(e)}


@router.post("/cache/clear")
def clear_cache(
    period: str = Query("day", enum=["minute", "hour", "day", "all"]),
    redis_client: redis.Redis = Depends(get_redis),
    _: bool = Depends(require_cache_admin)
):
    """Clear KPI cache (admin operation)"""
    try:
        cache_service = AnalyticsCacheService(redis_client)
        result = cache_service.invalidate_kpi_cache(time_period=period)
        return {"message": f"Cleared cache for {period}", **result}
    except Exception as e:
        return {"error": str(e)}
