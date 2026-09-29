package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading.worker.enabled", havingValue = "true", matchIfMissing = true)
public class OrderWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderWorker.class);
    private final OrderRepository orders;
    private final OrderService service;

    @Scheduled(fixedDelayString = "${trading.worker.delay-ms:1000}", initialDelayString = "${trading.worker.delay-ms:1000}")
    public void processPendingOrders() {
        for (Long orderId : orders.findPendingIds()) {
            try {
                service.executeOrder(orderId);
            } catch (RuntimeException exception) {
                log.error("Execution rolled back for order {}. It remains accepted for retry.", orderId, exception);
            }
        }
    }
}
