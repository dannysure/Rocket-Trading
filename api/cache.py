"""
Redis cache utilities for the Python API
Provides connection pooling and caching decorators
"""
import os
from dotenv import load_dotenv
import json
import logging
from functools import wraps
from typing import Optional, Any, Callable
import redis
from redis.connection import ConnectionPool

# Load .env file
load_dotenv()

logger = logging.getLogger(__name__)

# Global Redis connection pool
_redis_pool: Optional[ConnectionPool] = None
_redis_client: Optional[redis.Redis] = None


def init_redis():
    """Initialize Redis connection pool"""
    global _redis_pool, _redis_client
    
    host = os.getenv("REDIS_HOST", "localhost")
    port = int(os.getenv("REDIS_PORT", "6379"))
    db = int(os.getenv("REDIS_DB", "0"))
    password = os.getenv("REDIS_PASSWORD", None)
    
    try:
        _redis_pool = ConnectionPool(
            host=host,
            port=port,
            db=db,
            password=password,
            decode_responses=True,
            socket_connect_timeout=5,
            socket_keepalive=True,
            health_check_interval=30,
        )
        _redis_client = redis.Redis(connection_pool=_redis_pool)
        
        # Test connection
        _redis_client.ping()
        logger.info(f"Redis connected to {host}:{port}/{db}")
        return True
    except Exception as e:
        logger.error(f"Failed to connect to Redis: {e}")
        _redis_client = None
        return False


def get_redis_client() -> Optional[redis.Redis]:
    """Get Redis client instance"""
    if _redis_client is None:
        init_redis()
    return _redis_client


def close_redis():
    """Close Redis connection pool"""
    global _redis_pool, _redis_client
    if _redis_pool:
        _redis_pool.disconnect()
        _redis_client = None
        logger.info("Redis connection closed")


def cache(ttl_seconds: int = 300):
    """
    Decorator for caching function results in Redis
    
    Args:
        ttl_seconds: Time to live for cached values (default: 5 minutes)
    
    Usage:
        @cache(ttl_seconds=600)
        def expensive_function(user_id: int):
            return get_user_data(user_id)
    """
    def decorator(func: Callable) -> Callable:
        @wraps(func)
        def wrapper(*args, **kwargs):
            client = get_redis_client()
            if not client:
                # Redis unavailable, call function directly
                return func(*args, **kwargs)
            
            # Generate cache key from function name and arguments
            cache_key = f"{func.__module__}:{func.__name__}:{str(args)}:{str(kwargs)}"
            
            try:
                # Try to get from cache
                cached = client.get(cache_key)
                if cached:
                    logger.debug(f"Cache hit for {cache_key}")
                    return json.loads(cached)
            except Exception as e:
                logger.warning(f"Cache retrieval error: {e}")
            
            # Cache miss or error, call function
            result = func(*args, **kwargs)
            
            try:
                # Store in cache - handle Pydantic models
                if hasattr(result, 'dict'):  # Pydantic model
                    result_json = json.dumps(result.dict(), default=str)
                else:
                    result_json = json.dumps(result, default=str)
                
                client.setex(cache_key, ttl_seconds, result_json)
            except Exception as e:
                logger.warning(f"Cache storage error: {e}")
            
            return result
        
        return wrapper
    return decorator


def cache_key_prefix_delete(prefix: str) -> bool:
    """
    Delete all cache keys matching a prefix pattern
    
    Usage:
        cache_key_prefix_delete("portfolio:*")
        cache_key_prefix_delete("trading:orders:*")
    """
    client = get_redis_client()
    if not client:
        return False
    
    try:
        cursor = 0
        deleted_count = 0
        
        while True:
            cursor, keys = client.scan(cursor, match=prefix, count=100)
            if keys:
                deleted_count += client.delete(*keys)
            if cursor == 0:
                break
        
        logger.info(f"Deleted {deleted_count} cache keys with prefix '{prefix}'")
        return True
    except Exception as e:
        logger.error(f"Error deleting cache keys: {e}")
        return False


# ============================================================================
# CACHE INVALIDATION FUNCTIONS
# ============================================================================

def invalidate_client_portfolio(client_id: int) -> None:
    """Invalidate portfolio cache for a client"""
    cache_key_prefix_delete(f"portfolio:{client_id}")
    cache_key_prefix_delete(f"risk:{client_id}")


def invalidate_all_top_clients() -> None:
    """Invalidate all top-clients caches"""
    cache_key_prefix_delete("top-clients:*")


def invalidate_client_orders(client_id: int) -> None:
    """Invalidate order history cache for a client"""
    cache_key_prefix_delete(f"trading:orders:{client_id}:*")


def invalidate_trading_activity(client_id: Optional[int] = None) -> None:
    """Invalidate trading activity caches"""
    if client_id:
        cache_key_prefix_delete(f"trading:activity:{client_id}")
    else:
        cache_key_prefix_delete("trading:activity:*")


def invalidate_popular_instruments() -> None:
    """Invalidate popular instruments cache"""
    cache_key_prefix_delete("trading:popular:*")


def invalidate_client_watchlist(client_id: int) -> None:
    """Invalidate watchlist cache for a client"""
    cache_key_prefix_delete(f"watchlist:{client_id}")


def invalidate_popular_watched() -> None:
    """Invalidate popular watched instruments cache"""
    cache_key_prefix_delete("watchlist:popular:*")


def invalidate_analytics_kpis(client_id: Optional[int] = None) -> None:
    """Invalidate KPI cache"""
    if client_id:
        cache_key_prefix_delete(f"analytics:kpis:{client_id}")
    else:
        cache_key_prefix_delete("analytics:kpis:*")


def invalidate_analytics_alerts(client_id: Optional[int] = None) -> None:
    """Invalidate alerts cache"""
    if client_id:
        cache_key_prefix_delete(f"analytics:alerts:{client_id}:*")
    else:
        cache_key_prefix_delete("analytics:alerts:*")


def invalidate_audit_trail() -> None:
    """Invalidate audit trail cache"""
    cache_key_prefix_delete("analytics:audit:*")


def invalidate_order_lifecycle(order_id: int) -> None:
    """Invalidate order lifecycle cache"""
    get_redis_client().delete(f"analytics:order-lifecycle:{order_id}") if get_redis_client() else None


def invalidate_platform_overview() -> None:
    """Invalidate platform overview cache"""
    get_redis_client().delete("analytics:platform-overview") if get_redis_client() else None


def invalidate_all() -> None:
    """Nuclear option: invalidate all caches"""
    client = get_redis_client()
    if client:
        try:
            client.flushdb()
            logger.info("All Redis caches invalidated")
        except Exception as e:
            logger.error(f"Error clearing all caches: {e}")


def get_cached_value(key: str) -> Optional[Any]:
    """Get a specific cached value"""
    client = get_redis_client()
    if not client:
        return None
    
    try:
        cached = client.get(key)
        return json.loads(cached) if cached else None
    except Exception as e:
        logger.warning(f"Error retrieving cached value: {e}")
        return None


def set_cached_value(key: str, value: Any, ttl_seconds: int = 300) -> bool:
    """Set a cached value"""
    client = get_redis_client()
    if not client:
        return False
    
    try:
        client.setex(key, ttl_seconds, json.dumps(value, default=str))
        return True
    except Exception as e:
        logger.warning(f"Error setting cached value: {e}")
        return False
