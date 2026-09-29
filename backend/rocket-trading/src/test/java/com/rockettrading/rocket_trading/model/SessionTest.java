package com.rockettrading.rocket_trading.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    @Test
    @DisplayName("constructor stores session identity and expiry")
    void constructorStoresSessionIdentityAndExpiry() {
        Instant expiresAt = Instant.parse("2026-09-23T23:00:00Z");

        Session session = new Session(101L, expiresAt);

        assertAll(
                () -> assertEquals(101L, session.getSessionId()),
                () -> assertEquals(expiresAt, session.getExpiresAt())
        );
    }

    @Test
    @DisplayName("isActive returns true before expiry")
    void isActiveReturnsTrueBeforeExpiry() {
        Instant expiresAt = Instant.parse("2026-09-23T23:00:00Z");
        Session session = new Session(101L, expiresAt);

        assertTrue(session.isActive(Instant.parse("2026-09-23T22:00:00Z")));
    }

    @Test
    @DisplayName("isActive returns false at expiry")
    void isActiveReturnsFalseAtExpiry() {
        Instant expiresAt = Instant.parse("2026-09-23T23:00:00Z");
        Session session = new Session(101L, expiresAt);

        assertFalse(session.isActive(expiresAt));
    }

    @Test
    @DisplayName("revoke sets expiry to the provided instant")
    void revokeSetsExpiryToProvidedInstant() {
        Session session = new Session(101L, Instant.parse("2026-09-23T23:00:00Z"));
        Instant revokedAt = Instant.parse("2026-09-23T20:00:00Z");

        session.revoke(revokedAt);

        assertAll(
                () -> assertEquals(revokedAt, session.getExpiresAt()),
                () -> assertFalse(session.isActive(revokedAt))
        );
    }

    @Test
    @DisplayName("constructor rejects non-positive session id")
    void constructorRejectsNonPositiveSessionId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Session(0L, Instant.parse("2026-09-23T23:00:00Z"))
        );

        assertEquals("sessionId must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects null expiry")
    void constructorRejectsNullExpiry() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Session(101L, null)
        );

        assertEquals("expiresAt must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("isActive rejects null instant")
    void isActiveRejectsNullInstant() {
        Session session = new Session(101L, Instant.parse("2026-09-23T23:00:00Z"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> session.isActive(null)
        );

        assertEquals("asOf must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("revoke rejects null instant")
    void revokeRejectsNullInstant() {
        Session session = new Session(101L, Instant.parse("2026-09-23T23:00:00Z"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> session.revoke(null)
        );

        assertEquals("revokedAt must not be null", exception.getMessage());
    }
}
