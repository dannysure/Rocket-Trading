package com.rockettrading.rocket_trading;

import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientServiceTests {

    private Client client;
    private final long goodClientId = 1L;
    private final String goodClientName = "Test Client";
    private final String goodClientEmail = "test@example.com";
    private final long updatedClientId = 22L;
    private final String updatedClientName = "Updated Client";
    private final String updatedClientEmail = "updated@example.com";
    private final long badClientId = 0L;
    private final String badClientName = "   ";
    private final String badClientEmail = "invalid-email";

    @BeforeEach
    void setUp() {
        client = new Client(goodClientId, goodClientName, goodClientEmail);
    }

    @Test
    @DisplayName("client can be instantiated with identity fields")
    void clientCanBeInstantiated() {
        assertDoesNotThrow(() -> new Client(goodClientId, goodClientName, goodClientEmail));
    }

    @Test
    @DisplayName("constructor stores client identity details")
    void constructorStoresClientIdentityDetails() {
        assertAll(
                () -> assertEquals(goodClientId, client.getClientId()),
                () -> assertEquals(goodClientName, client.getName()),
                () -> assertEquals(goodClientEmail, client.getEmail())
        );
    }

    @Test
    @DisplayName("setters update client details")
    void settersUpdateClientDetails() {
        client.setClientId(updatedClientId);
        client.setName(updatedClientName);
        client.setEmail(updatedClientEmail);

        assertAll(
                () -> assertEquals(updatedClientId, client.getClientId()),
                () -> assertEquals(updatedClientName, client.getName()),
                () -> assertEquals(updatedClientEmail, client.getEmail())
        );
    }

    @Test
    @DisplayName("constructor rejects blank client name")
    void constructorRejectsBlankClientName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Client(goodClientId, "   ", goodClientEmail)
        );

        assertEquals("name must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects invalid client email")
    void constructorRejectsInvalidClientEmail() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Client(goodClientId, goodClientName, "invalid-email")
        );

        assertEquals("email must be a valid email address", exception.getMessage());
    }

    @Test
    @DisplayName("setter rejects non-positive client id")
    void setterRejectsNonPositiveClientId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.setClientId(badClientId)
        );

        assertEquals("clientId must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("setter rejects invalid email updates")
    void setterRejectsInvalidEmailUpdates() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.setEmail(badClientEmail)
        );

        assertEquals("email must be a valid email address", exception.getMessage());
    }

    @Test
    @DisplayName("register returns client registration details")
    void registerReturnsClientRegistrationDetails() {
        Instant registeredAt = Instant.parse("2026-09-23T15:00:00Z");

        Client.Registration registration = client.register(registeredAt);

        assertAll(
                () -> assertEquals(goodClientId, registration.getClientId()),
                () -> assertEquals(goodClientName, registration.getName()),
                () -> assertEquals(goodClientEmail, registration.getEmail()),
                () -> assertEquals(registeredAt, registration.getRegisteredAt())
        );
    }

    @Test
    @DisplayName("sign in creates an active session for the client")
    void signInCreatesAnActiveSessionForTheClient() {
        Instant signedInAt = Instant.parse("2026-09-23T15:00:00Z");

        Client.SignIn signIn = client.signIn(goodClientEmail, signedInAt);
        Session session = signIn.getSession();

        assertAll(
                () -> assertEquals(goodClientId, signIn.getClientId()),
                () -> assertEquals(signedInAt, signIn.getSignedInAt()),
                () -> assertNotNull(session),
                () -> assertTrue(session.getSessionId() > 0),
                () -> assertEquals(signedInAt.plus(Duration.ofHours(8)), session.getExpiresAt()),
                () -> assertTrue(session.isActive(signedInAt.plus(Duration.ofHours(1))))
        );
    }

    @Test
    @DisplayName("sign in rejects email that does not match the client")
    void signInRejectsEmailThatDoesNotMatchTheClient() {
        Instant signedInAt = Instant.parse("2026-09-23T15:00:00Z");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.signIn("someone-else@example.com", signedInAt)
        );

        assertEquals("email must match the registered client email", exception.getMessage());
    }

    @Test
    @DisplayName("sign in trims email input before matching")
    void signInTrimsEmailInputBeforeMatching() {
        Instant signedInAt = Instant.parse("2026-09-23T15:00:00Z");

        Client.SignIn signIn = client.signIn("  test@example.com  ", signedInAt);

        assertEquals(goodClientId, signIn.getClientId());
    }
}
