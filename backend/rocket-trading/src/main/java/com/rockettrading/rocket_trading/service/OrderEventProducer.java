package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.KafkaConfig;
import com.rockettrading.rocket_trading.dto.event.OrderAcceptedEvent;
import com.rockettrading.rocket_trading.dto.event.OrderFilledEvent;
import com.rockettrading.rocket_trading.dto.event.OrderRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

/**
 * Service for publishing order events to Kafka topics.
 * Events are published asynchronously and serve as triggers for workers and analytics.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventProducer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish an OrderAcceptedEvent to trigger execution workers.
     * Partitioned by orderId to maintain per-order ordering.
     *
     * @param event the order accepted event
     */
    public void publishOrderAccepted(OrderAcceptedEvent event) {
        try {
            String key = String.valueOf(event.orderId());
            sendAndWait(KafkaConfig.ORDER_ACCEPTED_TOPIC, key, event);
            log.debug("Published OrderAcceptedEvent for orderId: {}, clientId: {}", event.orderId(), event.clientId());
        } catch (Exception e) {
            log.error("Failed to publish OrderAcceptedEvent for orderId: {}", event.orderId(), e);
            throw new RuntimeException("Failed to publish order accepted event", e);
        }
    }

    /**
     * Publish an OrderFilledEvent to analytics and notification consumers.
     * Partitioned by orderId to maintain per-order ordering.
     *
     * @param event the order filled event
     */
    public void publishOrderFilled(OrderFilledEvent event) {
        try {
            String key = String.valueOf(event.orderId());
            sendAndWait(KafkaConfig.ORDER_FILLED_TOPIC, key, event);
            log.info("Published OrderFilledEvent for orderId: {}, fillId: {}, executedPrice: {}",
                    event.orderId(), event.fillId(), event.executedPrice());
        } catch (Exception e) {
            log.error("Failed to publish OrderFilledEvent for orderId: {}", event.orderId(), e);
            throw new RuntimeException("Failed to publish order filled event", e);
        }
    }

    /**
     * Publish an OrderRejectedEvent to analytics and notification consumers.
     * Partitioned by orderId to maintain per-order ordering.
     *
     * @param event the order rejected event
     */
    public void publishOrderRejected(OrderRejectedEvent event) {
        try {
            String key = String.valueOf(event.orderId());
            sendAndWait(KafkaConfig.ORDER_REJECTED_TOPIC, key, event);
            log.info("Published OrderRejectedEvent for orderId: {}, reason: {}",
                    event.orderId(), event.rejectionReason());
        } catch (Exception e) {
            log.error("Failed to publish OrderRejectedEvent for orderId: {}", event.orderId(), e);
            throw new RuntimeException("Failed to publish order rejected event", e);
        }
    }

    private void sendAndWait(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing Kafka event", exception);
        } catch (ExecutionException exception) {
            throw new RuntimeException("Kafka broker rejected event", exception.getCause());
        }
    }
}
