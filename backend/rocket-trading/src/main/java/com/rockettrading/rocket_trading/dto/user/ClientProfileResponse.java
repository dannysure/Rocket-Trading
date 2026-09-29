package com.rockettrading.rocket_trading.dto.user;

import com.rockettrading.rocket_trading.model.Client;

public record ClientProfileResponse(
        long clientId,
        String name,
        String email
) {
    public static ClientProfileResponse from(Client client) {
        return new ClientProfileResponse(client.getClientId(), client.getName(), client.getEmail());
    }
}
