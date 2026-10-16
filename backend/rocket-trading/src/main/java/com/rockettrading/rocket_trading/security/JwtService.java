package com.rockettrading.rocket_trading.security;

import com.rockettrading.rocket_trading.config.JwtProperties;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    // Published in .env.example and old compose files, so anyone could sign tokens with it (9.3)
    static final String PUBLISHED_DEVELOPMENT_SECRET = "change-me-development-jwt-secret-1234567890";

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

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
                .claim("role", client.getRole())
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
        String role = claims.get("role", String.class);
        return new AuthenticatedClient(clientId, sessionId, email, role == null ? "CLIENT" : role);
    }

    private synchronized SecretKey signingKey() {
        if (signingKey == null) {
            signingKey = createSigningKey();
        }
        return signingKey;
    }

    private SecretKey createSigningKey() {
        String configuredSecret = jwtProperties.getSecret();
        if (configuredSecret == null || configuredSecret.isBlank() || PUBLISHED_DEVELOPMENT_SECRET.equals(configuredSecret)) {
            // Never sign with a guessable key. A random one is safe but lasts only until restart,
            // so everyone signs in again and other services cannot verify tokens: set JWT_SECRET.
            log.warn("JWT_SECRET is unset or the published development value; using a random signing key "
                    + "for this run. Set JWT_SECRET (e.g. `openssl rand -base64 48`) so sessions survive restarts.");
            byte[] random = new byte[64];
            new SecureRandom().nextBytes(random);
            return Keys.hmacShaKeyFor(random);
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
