package com.rockettrading.rocket_trading.dto.auth;

import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;

import java.time.Instant;

public record SessionResponse(
        long clientId,
        long sessionId,
        Instant expiresAt,
        String accessToken,
        String tokenType,
        String name,
        String email,
        String role
) {
    public static SessionResponse from(Client client, Session session, String accessToken) {
        return new SessionResponse(
                client.getClientId(),
                session.getSessionId(),
                session.getExpiresAt(),
                accessToken,
                "Bearer",
                client.getName(),
                client.getEmail(),
                client.getRole()
        );
    }
}
