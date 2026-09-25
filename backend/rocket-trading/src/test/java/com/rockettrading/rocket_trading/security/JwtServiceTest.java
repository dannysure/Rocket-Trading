package com.rockettrading.rocket_trading.security;

import com.rockettrading.rocket_trading.config.JwtProperties;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtServiceTest {

    @Test
    void generatesAndParsesBearerClaims() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("rocket-trading");
        jwtProperties.setSecret("change-me-development-jwt-secret-1234567890");
        jwtProperties.setExpirationHours(8);
        JwtService jwtService = new JwtService(jwtProperties);

        Client client = new Client(42L, "Joanna", "joanna@example.com");
        Session session = new Session(1001L, Instant.now().plusSeconds(300));

        String token = jwtService.generateToken(client, session);
        AuthenticatedClient authenticatedClient = jwtService.parseToken(token);

        assertEquals(42L, authenticatedClient.clientId());
        assertEquals(1001L, authenticatedClient.sessionId());
        assertEquals("joanna@example.com", authenticatedClient.email());
    }
}
