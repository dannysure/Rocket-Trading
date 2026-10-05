package com.rockettrading.rocket_trading.config;

/**
 * WebSocket Configuration for real-time order status and portfolio updates
 * DISABLED: Spring messaging dependency version mismatch - will enable after dependency resolution
 * Uses STOMP protocol with SimpMessagingTemplate for server-initiated pushes
 */
/*
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure STOMP endpoint for clients to connect
     */
/*
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();
    }

    /**
     * Configure message broker for pub/sub messaging
     */
/*
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory message broker
        config.enableSimpleBroker("/topic", "/queue");
        
        // Set prefix for messages sent from client to server
        config.setApplicationDestinationPrefixes("/app");
        
        // Set prefix for user-specific destinations (for direct messages)
        config.setUserDestinationPrefix("/user");
    }
}
*/

// WebSocket support will be added after resolving Spring Messaging dependency
public class WebSocketConfig {
    // Placeholder - will be re-enabled after dependency version alignment
}
