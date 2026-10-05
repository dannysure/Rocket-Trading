"""
Cache management endpoints for admin/debugging
POST /api/cache/invalidate/{scope}
GET /api/cache/stats
"""
from fastapi import APIRouter, HTTPException
from ..cache import (
    invalidate_all,
    invalidate_client_portfolio,
    invalidate_client_orders,
    invalidate_trading_activity,
    invalidate_popular_instruments,
    invalidate_client_watchlist,
    invalidate_popular_watched,
    invalidate_analytics_kpis,
    invalidate_analytics_alerts,
    invalidate_platform_overview,
    get_redis_client,
)
import logging

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/cache", tags=["cache-management"])


@router.post("/invalidate/all")
def invalidate_cache_all():
    """Clear all caches (use with caution!)"""
    try:
        invalidate_all()
        return {"status": "success", "message": "All caches cleared"}
    except Exception as e:
        logger.error(f"Error clearing caches: {e}")
        raise HTTPException(status_code=500, detail=f"Error: {str(e)}")


@router.post("/invalidate/client/{client_id}")
def invalidate_client_cache(client_id: int):
    """Invalidate all caches for a specific client (portfolio, orders, watchlist, etc)"""
    try:
        invalidate_client_portfolio(client_id)
        invalidate_client_orders(client_id)
        invalidate_client_watchlist(client_id)
        invalidate_analytics_kpis(client_id)
        invalidate_analytics_alerts(client_id)
        
        return {
            "status": "success",
            "message": f"All caches for client {client_id} cleared"
        }
    except Exception as e:
        logger.error(f"Error clearing client {client_id} caches: {e}")
        raise HTTPException(status_code=500, detail=f"Error: {str(e)}")


@router.post("/invalidate/trading")
def invalidate_trading_cache():
    """Invalidate all trading-related caches"""
    try:
        invalidate_trading_activity()
        invalidate_popular_instruments()
        return {
            "status": "success",
            "message": "All trading caches cleared"
        }
    except Exception as e:
        logger.error(f"Error clearing trading caches: {e}")
        raise HTTPException(status_code=500, detail=f"Error: {str(e)}")


@router.post("/invalidate/watchlist")
def invalidate_watchlist_cache():
    """Invalidate all watchlist-related caches"""
    try:
        invalidate_popular_watched()
        return {
            "status": "success",
            "message": "All watchlist caches cleared"
        }
    except Exception as e:
        logger.error(f"Error clearing watchlist caches: {e}")
        raise HTTPException(status_code=500, detail=f"Error: {str(e)}")


@router.post("/invalidate/analytics")
def invalidate_analytics_cache():
    """Invalidate all analytics-related caches"""
    try:
        invalidate_analytics_kpis()
        invalidate_analytics_alerts()
        invalidate_platform_overview()
        return {
            "status": "success",
            "message": "All analytics caches cleared"
        }
    except Exception as e:
        logger.error(f"Error clearing analytics caches: {e}")
        raise HTTPException(status_code=500, detail=f"Error: {str(e)}")


@router.get("/stats")
def get_cache_stats():
    """Get Redis cache statistics"""
    try:
        client = get_redis_client()
        if not client:
            return {"status": "disconnected", "message": "Redis is not connected"}
        
        info = client.info("memory")
        dbsize = client.dbsize()
        
        return {
            "status": "connected",
            "memory_used": info.get("used_memory_human", "N/A"),
            "memory_peak": info.get("used_memory_peak_human", "N/A"),
            "total_keys": dbsize,
            "memory_fragmentation": info.get("mem_fragmentation_ratio", 0)
        }
    except Exception as e:
        logger.error(f"Error getting cache stats: {e}")
        raise HTTPException(status_code=500, detail=f"Error: {str(e)}")
