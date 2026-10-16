package com.rockettrading.rocket_trading.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for caching and rate limiting
 * Enabled by: spring.redis.enabled=true (default)
 */
@Configuration
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    /**
     * RedisTemplate bean for String keys and JSON-serialized values
     * Used for:
     * - Finished-order cache (order:{clientId}:{orderId})
     * - Refresh token storage (refresh-token:{username})
     * - Rate limit counters (rate-limit:{clientId}:{minute})
     * - Analytics KPI cache (kpi:{metric}:{dimension})
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use StringRedisSerializer for keys (human-readable in Redis CLI)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        
        // JSON values; orders carry Instants, which plain Jackson cannot write (every cache write used to fail)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        jsonSerializer.configure(mapper -> mapper.registerModule(new JavaTimeModule()));

        // String key-value operations
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);

        // Hash operations (for storing complex objects)
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
