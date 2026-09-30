package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.event.OrderFilledEvent;
import com.rockettrading.rocket_trading.event.OrderSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service to publish trading events to Kafka
 * Handles: order submissions, fills, rejections, portfolio updates
 */
@Slf4j
@Service
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish when a client submits an order
     */
    public void publishOrderSubmitted(OrderSubmittedEvent event) {
        event.setEventId(UUID.randomUUID().toString());
        Message<OrderSubmittedEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "order-submitted")
                .setHeader(KafkaHeaders.MESSAGE_KEY, String.valueOf(event.getClientId()))
                .build();

        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("OrderSubmittedEvent published: orderId={}, clientId={}, eventId={}",
                        event.getOrderId(), event.getClientId(), event.getEventId());
            } else {
                log.error("Failed to publish OrderSubmittedEvent: orderId={}", event.getOrderId(), ex);
            }
        });
    }

    /**
     * Publish when an order is filled
     */
    public void publishOrderFilled(OrderFilledEvent event) {
        event.setEventId(UUID.randomUUID().toString());
        Message<OrderFilledEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "order-filled")
                .setHeader(KafkaHeaders.MESSAGE_KEY, String.valueOf(event.getClientId()))
                .build();

        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("OrderFilledEvent published: orderId={}, fillId={}, clientId={}, eventId={}",
                        event.getOrderId(), event.getFillId(), event.getClientId(), event.getEventId());
            } else {
                log.error("Failed to publish OrderFilledEvent: orderId={}", event.getOrderId(), ex);
            }
        });
    }

    /**
     * Publish when an order is rejected
     */
    public void publishOrderRejected(long orderId, long clientId, String reason) {
        String eventId = UUID.randomUUID().toString();
        Message<String> message = MessageBuilder
                .withPayload(String.format("{\"orderId\":%d,\"clientId\":%d,\"reason\":\"%s\",\"eventId\":\"%s\"}", 
                        orderId, clientId, reason, eventId))
                .setHeader(KafkaHeaders.TOPIC, "order-rejected")
                .setHeader(KafkaHeaders.MESSAGE_KEY, String.valueOf(clientId))
                .build();

        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("OrderRejectedEvent published: orderId={}, reason={}", orderId, reason);
            } else {
                log.error("Failed to publish OrderRejectedEvent: orderId={}", orderId, ex);
            }
        });
    }

    /**
     * Publish portfolio update events
     */
    public void publishPortfolioUpdated(long clientId, long accountId) {
        String eventId = UUID.randomUUID().toString();
        Message<String> message = MessageBuilder
                .withPayload(String.format("{\"clientId\":%d,\"accountId\":%d,\"eventId\":\"%s\"}", 
                        clientId, accountId, eventId))
                .setHeader(KafkaHeaders.TOPIC, "portfolio-updated")
                .setHeader(KafkaHeaders.MESSAGE_KEY, String.valueOf(clientId))
                .build();

        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("PortfolioUpdatedEvent published: clientId={}, accountId={}", clientId, accountId);
            } else {
                log.error("Failed to publish PortfolioUpdatedEvent: clientId={}", clientId, ex);
            }
        });
    }
}
