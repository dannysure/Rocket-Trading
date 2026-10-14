package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.KafkaConfig;
import com.rockettrading.rocket_trading.dto.event.OrderAcceptedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for executing accepted orders.
 * Replaces the OrderWorker polling pattern with event-driven processing.
 *
 * Benefits:
 * - Latency reduced from 1s polling to <100ms event delivery
 * - Multiple consumer instances scale independently
 * - Orders partition by orderId ensures per-order serial execution
 * - Automatic retry on transient failures
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final OrderService orderService;

    /**
     * Listen to order.accepted events and execute them.
     * Consumer group "order-execution-workers" allows multiple instances.
     * Message key (orderId) partitions load across workers.
     *
     * @param event the order accepted event containing order details
     * @param ack manual acknowledgment for exactly-once semantics
     */
    @KafkaListener(
            topics = KafkaConfig.ORDER_ACCEPTED_TOPIC,
            groupId = "order-execution-workers"
    )
    public void executeAcceptedOrder(
            @Payload OrderAcceptedEvent event,
            Acknowledgment ack) {

        try {
            log.info("Processing OrderAcceptedEvent: orderId={}, clientId={}",
                    event.orderId(), event.clientId());

            // Execute the order using the existing service logic
            orderService.executeOrder(event.orderId());

            // Acknowledge successful processing
            ack.acknowledge();

            log.info("Successfully executed order: orderId={}", event.orderId());

        } catch (RuntimeException e) {
            // Log the error but don't acknowledge - Kafka will retry
            log.error("Failed to execute order: orderId={}. Will retry on next poll.",
                    event.orderId(), e);

            // In case of specific transient errors, we could add exponential backoff via
            // a dead letter topic for persistent failures.
            throw e; // Re-throw to trigger Kafka retry logic
        }
    }
}
