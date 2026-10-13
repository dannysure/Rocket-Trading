package com.rockettrading.rocket_trading.security;

import com.rockettrading.rocket_trading.config.JwtProperties;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(Client client, Session session) {
        Date issuedAt = new Date();
        Date expiresAt = Date.from(session.getExpiresAt());

        return Jwts.builder()
                .subject(String.valueOf(client.getClientId()))
                .issuer(jwtProperties.getIssuer())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .claim("clientId", client.getClientId())
                .claim("sessionId", session.getSessionId())
                .claim("email", client.getEmail())
                .signWith(signingKey())
                .compact();
    }

    public AuthenticatedClient parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
            .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long clientId = claims.get("clientId", Number.class).longValue();
        long sessionId = claims.get("sessionId", Number.class).longValue();
        String email = claims.get("email", String.class);
        return new AuthenticatedClient(clientId, sessionId, email);
    }

    private SecretKey signingKey() {
        String configuredSecret = jwtProperties.getSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured");
        }

        byte[] rawSecret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (rawSecret.length >= 32) {
            return Keys.hmacShaKeyFor(rawSecret);
        }

        try {
            byte[] decodedSecret = Decoders.BASE64.decode(configuredSecret);
            if (decodedSecret.length < 32) {
                throw new IllegalStateException("JWT secret must be at least 32 bytes");
            }
            return Keys.hmacShaKeyFor(decodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes or valid base64", exception);
        }
    }
}
