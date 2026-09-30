package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.portfolio.PortfolioSummaryResponse;
import com.rockettrading.rocket_trading.service.PortfolioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller for real-time order and portfolio updates
 * Routes: /app/subscribe/{clientId}, /app/order-updates/{clientId}
 */
@Slf4j
@Controller
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PortfolioService portfolioService;

    public WebSocketController(SimpMessagingTemplate messagingTemplate, PortfolioService portfolioService) {
        this.messagingTemplate = messagingTemplate;
        this.portfolioService = portfolioService;
    }

    /**
     * Subscribe to real-time updates for a client's portfolio
     * Client sends: /app/subscribe/{clientId}
     * Receives: /topic/portfolio/{clientId}
     */
    @MessageMapping("/subscribe/{clientId}")
    public void subscribeToPortfolioUpdates(@DestinationVariable long clientId) {
        try {
            log.info("Client {} subscribed to portfolio updates", clientId);
            // Send initial portfolio state
            PortfolioSummaryResponse portfolio = portfolioService.getPortfolioSummary(clientId);
            messagingTemplate.convertAndSend(
                    "/topic/portfolio/" + clientId,
                    ApiResponse.success(portfolio)
            );
        } catch (Exception e) {
            log.error("Error handling subscription for client {}", clientId, e);
            messagingTemplate.convertAndSend(
                    "/topic/portfolio/" + clientId,
                    ApiResponse.error("Subscription failed: " + e.getMessage())
            );
        }
    }

    /**
     * Send order status update to a specific client
     * Internal method called by OrderService
     */
    public void sendOrderUpdate(long clientId, Object orderUpdate) {
        messagingTemplate.convertAndSend(
                "/topic/orders/" + clientId,
                ApiResponse.success(orderUpdate)
        );
        log.debug("Order update sent to client {}", clientId);
    }

    /**
     * Send portfolio update to a specific client
     * Internal method called by OrderService when portfolio changes
     */
    public void sendPortfolioUpdate(long clientId, PortfolioSummaryResponse portfolio) {
        messagingTemplate.convertAndSend(
                "/topic/portfolio/" + clientId,
                ApiResponse.success(portfolio)
        );
        log.debug("Portfolio update sent to client {}", clientId);
    }

    /**
     * Health check endpoint for WebSocket
     */
    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public String ping(String message) {
        return "pong";
    }
}
