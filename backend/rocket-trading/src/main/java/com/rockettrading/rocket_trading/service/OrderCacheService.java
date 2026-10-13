package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.order.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Cache service for order results
 * Reduces database queries for frequently accessed orders
 * TTL: 30 seconds (order status changes frequently)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
public class OrderCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.cache.order.ttl:30}")
    private long orderCacheTtl;

    private static final String CACHE_KEY_PREFIX = "order:";

    /**
     * Store order result in cache
     * Key format: "order:{orderId}"
     */
    public void cacheOrder(long orderId, OrderResponse order) {
        try {
            String key = CACHE_KEY_PREFIX + orderId;
            redisTemplate.opsForValue().set(key, order, orderCacheTtl, TimeUnit.SECONDS);
            log.debug("Cached order {}: {} (TTL: {}s)", orderId, order.status(), orderCacheTtl);
        } catch (Exception e) {
            log.warn("Failed to cache order {}: {}", orderId, e.getMessage());
            // Don't throw - cache failures should not block order operations
        }
    }

    /**
     * Retrieve order from cache
     * Returns null if not found or expired
     */
    public OrderResponse getOrder(long orderId) {
        try {
            String key = CACHE_KEY_PREFIX + orderId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof OrderResponse order) {
                log.debug("Cache hit for order {}", orderId);
                return order;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to retrieve cached order {}: {}", orderId, e.getMessage());
            return null; // Fall back to database
        }
    }

    /**
     * Invalidate order cache (when order status changes)
     * Called after order.filled or order.rejected events
     */
    public void invalidateOrder(long orderId) {
        try {
            String key = CACHE_KEY_PREFIX + orderId;
            Boolean deleted = redisTemplate.delete(key);
            log.debug("Invalidated order cache for {}: {}", orderId, deleted);
        } catch (Exception e) {
            log.warn("Failed to invalidate order cache {}: {}", orderId, e.getMessage());
        }
    }

    /**
     * Check if order is in cache
     */
    public boolean isOrderCached(long orderId) {
        try {
            String key = CACHE_KEY_PREFIX + orderId;
            Boolean exists = redisTemplate.hasKey(key);
            return exists != null && exists;
        } catch (Exception e) {
            log.warn("Failed to check cache for order {}: {}", orderId, e.getMessage());
            return false;
        }
    }
}
