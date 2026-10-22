package com.rockettrading.rocket_trading.security;

import com.rockettrading.rocket_trading.config.JwtProperties;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void neverAcceptsTokensSignedWithThePublishedDevelopmentSecret() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("rocket-trading");
        jwtProperties.setSecret(JwtService.PUBLISHED_DEVELOPMENT_SECRET);
        JwtService jwtService = new JwtService(jwtProperties);

        // Anyone who has read .env.example could mint this operations token
        String forged = Jwts.builder()
                .issuer("rocket-trading")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .claim("clientId", 1L).claim("sessionId", 1L).claim("role", "OPERATIONS")
                .signWith(Keys.hmacShaKeyFor(JwtService.PUBLISHED_DEVELOPMENT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> jwtService.parseToken(forged));
    }
}
