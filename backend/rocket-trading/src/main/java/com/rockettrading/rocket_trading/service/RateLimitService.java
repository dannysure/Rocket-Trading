package com.rockettrading.rocket_trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting service using Redis atomic counters
 * Prevents clients from placing too many orders per minute
 * TTL: 60 seconds (reset every minute)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.rate-limit.orders-per-minute:100}")
    private long ordersPerMinute;

    private static final String RATE_LIMIT_PREFIX = "rate-limit:orders:";

    /**
     * Check if client has exceeded rate limit for this minute
     * Returns true if request is allowed, false if rate limit exceeded
     * 
     * Uses atomic INCR operation to prevent race conditions
     */
    public boolean isAllowed(long clientId) {
        try {
            String key = RATE_LIMIT_PREFIX + clientId + ":" + getCurrentMinute();
            
            // Atomic increment
            Long count = redisTemplate.opsForValue().increment(key);
            
            // Set expiration on first request of the minute
            if (count == 1) {
                redisTemplate.expire(key, 61, TimeUnit.SECONDS); // 61s to be safe
            }
            
            if (count > ordersPerMinute) {
                log.warn("Rate limit exceeded for client {}: {} orders this minute", clientId, count);
                return false;
            }
            
            log.debug("Rate limit check for client {}: {}/{} orders", clientId, count, ordersPerMinute);
            return true;
        } catch (Exception e) {
            log.warn("Failed to check rate limit for client {}: {}", clientId, e.getMessage());
            return true; // Fail open - allow request if Redis fails
        }
    }

    /**
     * Get current order count for client this minute
     */
    public long getOrderCount(long clientId) {
        try {
            String key = RATE_LIMIT_PREFIX + clientId + ":" + getCurrentMinute();
            Object count = redisTemplate.opsForValue().get(key);
            return count instanceof Long ? (Long) count : (count instanceof String ? Long.parseLong((String) count) : 0);
        } catch (Exception e) {
            log.warn("Failed to get order count for client {}: {}", clientId, e.getMessage());
            return 0;
        }
    }

    /**
     * Get remaining orders allowed for client this minute
     */
    public long getRemainingOrders(long clientId) {
        long used = getOrderCount(clientId);
        return Math.max(0, ordersPerMinute - used);
    }

    /**
     * Reset rate limit for a client (admin action)
     */
    public void resetRateLimit(long clientId) {
        try {
            String key = RATE_LIMIT_PREFIX + clientId + ":" + getCurrentMinute();
            redisTemplate.delete(key);
            log.info("Reset rate limit for client {}", clientId);
        } catch (Exception e) {
            log.warn("Failed to reset rate limit for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Get current minute as Unix timestamp (in minutes)
     */
    private long getCurrentMinute() {
        return Instant.now().getEpochSecond() / 60;
    }
}
