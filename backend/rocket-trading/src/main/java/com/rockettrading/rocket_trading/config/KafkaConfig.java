package com.rockettrading.rocket_trading.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for event-driven order processing.
 * Configures topics, producers, and consumers for order events.
 * Uses Spring's auto-configuration for producer/consumer factories.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    // Topic names
    public static final String ORDER_ACCEPTED_TOPIC = "order.accepted";
    public static final String ORDER_FILLED_TOPIC = "order.filled";
    public static final String ORDER_REJECTED_TOPIC = "order.rejected";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Admin configuration for creating topics.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Create order.accepted topic for triggering execution workers.
     * 10 partitions for horizontal scaling of workers.
     * 7 day retention for compliance.
     */
    @Bean
    public NewTopic orderAcceptedTopic() {
        return TopicBuilder
                .name(ORDER_ACCEPTED_TOPIC)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000)) // 7 days
                .build();
    }

    /**
     * Create order.filled topic for analytics and notifications.
     * 10 partitions for throughput.
     * 90 day retention for compliance.
     */
    @Bean
    public NewTopic orderFilledTopic() {
        return TopicBuilder
                .name(ORDER_FILLED_TOPIC)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", String.valueOf(90L * 24 * 60 * 60 * 1000)) // 90 days
                .build();
    }

    /**
     * Create order.rejected topic for notifications and analytics.
     * 10 partitions for throughput.
     * 30 day retention.
     */
    @Bean
    public NewTopic orderRejectedTopic() {
        return TopicBuilder
                .name(ORDER_REJECTED_TOPIC)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", String.valueOf(30L * 24 * 60 * 60 * 1000)) // 30 days
                .build();
    }

    @Bean
    public NewTopic orderAcceptedDeadLetterTopic() {
        return TopicBuilder.name(ORDER_ACCEPTED_TOPIC + ".DLT").partitions(10).replicas(1).build();
    }

    @Bean
    public NewTopic orderFilledDeadLetterTopic() {
        return TopicBuilder.name(ORDER_FILLED_TOPIC + ".DLT").partitions(10).replicas(1).build();
    }

    @Bean
    public NewTopic orderRejectedDeadLetterTopic() {
        return TopicBuilder.name(ORDER_REJECTED_TOPIC + ".DLT").partitions(10).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}

