package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.order.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Caches finished (FILLED or REJECTED) orders, which never change again, so a cached entry can never
 * show an out-of-date status (BR-07). Entries are keyed by the owning client as well as the order, so
 * a lookup can only ever return the caller's own order (BR-02).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
public class OrderCacheService {
    private static final Set<String> FINAL_STATUSES = Set.of("FILLED", "REJECTED");
    private static final String CACHE_KEY_PREFIX = "order:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.cache.order.ttl:30}")
    private long orderCacheTtl;

    /** Stores the order only once it is final; orders still in flight are always read from the database. */
    public void cacheOrder(long clientId, OrderResponse order) {
        if (!FINAL_STATUSES.contains(order.status())) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(clientId, order.orderId()), order, orderCacheTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Cache failures must not block order operations
            log.warn("Failed to cache order {}: {}", order.orderId(), e.getMessage());
        }
    }

    /** The caller's own cached order, or null if it is not cached (or belongs to someone else). */
    public OrderResponse getOrder(long clientId, long orderId) {
        try {
            Object cached = redisTemplate.opsForValue().get(key(clientId, orderId));
            return cached instanceof OrderResponse order ? order : null;
        } catch (Exception e) {
            log.warn("Failed to retrieve cached order {}: {}", orderId, e.getMessage());
            return null; // Fall back to database
        }
    }

    static String key(long clientId, long orderId) {
        return CACHE_KEY_PREFIX + clientId + ":" + orderId;
    }
}
