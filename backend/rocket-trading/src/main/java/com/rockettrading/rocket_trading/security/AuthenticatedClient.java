package com.rockettrading.rocket_trading.security;

public record AuthenticatedClient(long clientId, long sessionId, String email) {
}
