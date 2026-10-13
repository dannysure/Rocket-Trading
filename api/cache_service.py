"""
Redis caching layer for analytics
Handles KPI cache retrieval and management
"""

import redis
import logging
from typing import Optional, Dict, Any
from datetime import datetime

logger = logging.getLogger(__name__)


class AnalyticsCacheService:
    """
    Retrieves cached analytics from Redis
    Sources: Kafka consumer updates cache, API reads from cache
    """

    def __init__(self, redis_client: redis.Redis):
        """
        Initialize cache service
        
        Args:
            redis_client: Redis connection instance
        """
        self.redis = redis_client

    def get_kpi_panel(self, time_period: str = "day") -> Dict[str, Any]:
        """
        Get KPI panel metrics from Redis cache
        
        Args:
            time_period: 'minute', 'hour', or 'day'
        
        Returns:
            Dictionary with: fills, gmv, rejection_count, top_symbols, top_clients
        """
        try:
            now = datetime.utcnow()

            if time_period == "day":
                period_str = now.strftime('%Y%m%d')
            elif time_period == "hour":
                period_str = now.strftime('%Y%m%d%H')
            else:
                period_str = now.strftime('%Y%m%d%H%M')

            # Get metrics from Redis
            fills = int(self.redis.get(f"kpi:fills:{time_period}:{period_str}") or 0)
            gmv = float(self.redis.get(f"kpi:gmv:{time_period}:{period_str}") or 0)
            rejections = int(self.redis.get(f"kpi:rejections:{time_period}:{period_str}:total") or 0)

            # Get top symbols (most traded)
            top_symbols = self.redis.zrevrange(
                f"kpi:top-symbols:{time_period}:{period_str}",
                0, 4,
                withscores=True
            )
            top_symbols_list = [
                {"symbol": sym, "gmv": float(score)}
                for sym, score in top_symbols
            ]

            # Get top clients
            top_clients = self.redis.zrevrange(
                f"kpi:top-clients:{time_period}:{period_str}",
                0, 4,
                withscores=True
            )
            top_clients_list = [
                {"client_id": int(client.split(':')[1]), "gmv": float(score)}
                for client, score in top_clients
            ]

            return {
                'fills': fills,
                'gmv': gmv,
                'rejections': rejections,
                'fill_rate': (fills / (fills + rejections) * 100) if (fills + rejections) > 0 else 0,
                'avg_fill_size': gmv / fills if fills > 0 else 0,
                'top_symbols': top_symbols_list,
                'top_clients': top_clients_list,
                'time_period': time_period,
                'cached_at': datetime.utcnow().isoformat()
            }

        except Exception as e:
            logger.error(f"Error getting KPI panel: {e}")
            return {
                'fills': 0,
                'gmv': 0,
                'rejections': 0,
                'fill_rate': 0,
                'avg_fill_size': 0,
                'top_symbols': [],
                'top_clients': [],
                'time_period': time_period,
                'error': str(e)
            }

    def get_client_kpis(self, client_id: int, time_period: str = "day") -> Dict[str, Any]:
        """
        Get per-client KPI metrics
        
        Args:
            client_id: Client ID
            time_period: 'day', 'hour', 'minute'
        
        Returns:
            Client-specific metrics
        """
        try:
            now = datetime.utcnow()

            if time_period == "day":
                period_str = now.strftime('%Y%m%d')
            elif time_period == "hour":
                period_str = now.strftime('%Y%m%d%H')
            else:
                period_str = now.strftime('%Y%m%d%H%M')

            # Look for client in top clients sorted set
            client_key = f"client:{client_id}"
            clients_key = f"kpi:top-clients:{time_period}:{period_str}"
            
            gmv = float(self.redis.zscore(clients_key, client_key) or 0)

            # Get client fills (stored per client)
            fills = int(self.redis.get(f"kpi:client:{client_id}:fills:{period_str}") or 0)

            return {
                'client_id': client_id,
                'gmv': gmv,
                'fills': fills,
                'time_period': time_period,
                'cached_at': datetime.utcnow().isoformat()
            }

        except Exception as e:
            logger.error(f"Error getting client KPIs: {e}")
            return {
                'client_id': client_id,
                'gmv': 0,
                'fills': 0,
                'error': str(e)
            }

    def invalidate_kpi_cache(self, time_period: str = "day"):
        """
        Clear KPI cache (admin operation, force refresh)
        
        Args:
            time_period: 'day', 'hour', 'minute' or 'all'
        """
        try:
            now = datetime.utcnow()
            count = 0

            if time_period == "all":
                # Clear all KPI keys
                pattern = "kpi:*"
                keys = self.redis.keys(pattern)
                if keys:
                    count = self.redis.delete(*keys)
            else:
                if time_period == "day":
                    period_str = now.strftime('%Y%m%d')
                elif time_period == "hour":
                    period_str = now.strftime('%Y%m%d%H')
                else:
                    period_str = now.strftime('%Y%m%d%H%M')

                # Delete specific period keys
                keys_to_delete = [
                    f"kpi:fills:{time_period}:{period_str}",
                    f"kpi:gmv:{time_period}:{period_str}",
                    f"kpi:rejections:{time_period}:{period_str}:total",
                    f"kpi:top-symbols:{time_period}:{period_str}",
                    f"kpi:top-clients:{time_period}:{period_str}",
                ]
                count = self.redis.delete(*keys_to_delete)

            logger.info(f"Invalidated {count} KPI cache keys for {time_period}")
            return {"deleted": count}

        except Exception as e:
            logger.error(f"Error invalidating cache: {e}")
            return {"error": str(e)}

    def get_cache_stats(self) -> Dict[str, Any]:
        """Get overall cache statistics"""
        try:
            info = self.redis.info()
            return {
                'redis_version': info.get('redis_version'),
                'connected_clients': info.get('connected_clients'),
                'used_memory': info.get('used_memory_human'),
                'total_commands': info.get('total_commands_processed'),
                'keys_count': self.redis.dbsize()
            }
        except Exception as e:
            logger.error(f"Error getting cache stats: {e}")
            return {'error': str(e)}
