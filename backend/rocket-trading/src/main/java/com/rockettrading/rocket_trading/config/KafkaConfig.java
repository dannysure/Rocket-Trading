package com.rockettrading.rocket_trading.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for event streaming
 * Topics: orders, trades, fills, portfolio-updates
 * 
 * Disabled when kafka.enabled=false (for local development without Kafka)
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ============================================================================
    // ADMIN CONFIG: Create Topics
    // ============================================================================
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic orderSubmittedTopic() {
        return new NewTopic("order-submitted", 3, (short) 1);
    }

    @Bean
    public NewTopic orderAcceptedTopic() {
        return new NewTopic("order-accepted", 3, (short) 1);
    }

    @Bean
    public NewTopic orderFilledTopic() {
        return new NewTopic("order-filled", 3, (short) 1);
    }

    @Bean
    public NewTopic orderRejectedTopic() {
        return new NewTopic("order-rejected", 3, (short) 1);
    }

    @Bean
    public NewTopic portfolioUpdateTopic() {
        return new NewTopic("portfolio-updated", 3, (short) 1);
    }

    @Bean
    public NewTopic auditLogTopic() {
        return new NewTopic("audit-log", 3, (short) 1);
    }

    // ============================================================================
    // PRODUCER CONFIG: Serialize messages to JSON
    // ============================================================================
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public DefaultKafkaProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
}
