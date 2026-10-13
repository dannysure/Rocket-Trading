package com.rockettrading.rocket_trading.security;

public record AuthenticatedClient(long clientId, long sessionId, String email, String role) {
    public AuthenticatedClient(long clientId, long sessionId, String email) {
        this(clientId, sessionId, email, "CLIENT");
    }
}
