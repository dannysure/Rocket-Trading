package com.rockettrading.rocket_trading.dto.auth;

import com.rockettrading.rocket_trading.model.Client;

import java.time.Instant;

public record SessionResponse(
        long clientId,
        long sessionId,
        Instant expiresAt,
        String accessToken,
        String tokenType
) {
    public static SessionResponse from(Client.SignIn signIn, String accessToken) {
        return new SessionResponse(
                signIn.getClientId(),
                signIn.getSession().getSessionId(),
                signIn.getSession().getExpiresAt(),
                accessToken,
                "Bearer"
        );
    }
}
