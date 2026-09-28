package com.rockettrading.rocket_trading.dto.auth;

import com.rockettrading.rocket_trading.model.Client;

import java.time.Instant;

public record ClientRegistrationResponse(
        long clientId,
        String name,
        String email,
        Instant registeredAt
) {
    public static ClientRegistrationResponse from(Client.Registration registration) {
        return new ClientRegistrationResponse(
                registration.getClientId(),
                registration.getName(),
                registration.getEmail(),
                registration.getRegisteredAt()
        );
    }
}
