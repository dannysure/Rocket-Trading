package com.rockettrading.rocket_trading.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Entity
public class Client {
    @Id
    private long clientId;
    private String name;
    private String email;

    public Client() {
    }

    public Client(long clientId, String name, String email) {
        setClientId(clientId);
        setName(name);
        setEmail(email);
    }

    public Registration register() {
        return register(Instant.now());
    }

    public Registration register(Instant registeredAt) {
        requireRegisteredIdentity();
        requireTimestamp(registeredAt, "registeredAt");
        return new Registration(clientId, name, email, registeredAt);
    }

    public SignIn signIn() {
        return signIn(email, Instant.now());
    }

    public SignIn signIn(String emailAddress) {
        return signIn(emailAddress, Instant.now());
    }

    public SignIn signIn(String emailAddress, Instant signedInAt) {
        requireRegisteredIdentity();
        requireTimestamp(signedInAt, "signedInAt");

        if (emailAddress == null || !email.equalsIgnoreCase(emailAddress.trim())) {
            throw new IllegalArgumentException("email must match the registered client email");
        }

        Session session = new Session(generateSessionId(signedInAt), signedInAt.plus(Duration.ofHours(8)));
        return new SignIn(clientId, session, signedInAt);
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        if (clientId <= 0) {
            throw new IllegalArgumentException("clientId must be positive");
        }
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name.trim();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (email == null || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("email must be a valid email address");
        }
        this.email = email.trim();
    }

    private void requireRegisteredIdentity() {
        if (clientId <= 0 || name == null || email == null) {
            throw new IllegalStateException("client must have a complete identity before registration or sign-in");
        }
    }

    private void requireTimestamp(Instant timestamp, String fieldName) {
        if (timestamp == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    private long generateSessionId(Instant signedInAt) {
        long generatedSessionId = Math.abs((long) Objects.hash(clientId, email, signedInAt));
        return generatedSessionId == 0 ? 1L : generatedSessionId;
    }

    public static final class Registration {
        private final long clientId;
        private final String name;
        private final String email;
        private final Instant registeredAt;

        public Registration(long clientId, String name, String email, Instant registeredAt) {
            this.clientId = clientId;
            this.name = name;
            this.email = email;
            this.registeredAt = registeredAt;
        }

        public long getClientId() {
            return clientId;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public Instant getRegisteredAt() {
            return registeredAt;
        }
    }

    public static final class SignIn {
        private final long clientId;
        private final Session session;
        private final Instant signedInAt;

        public SignIn(long clientId, Session session, Instant signedInAt) {
            this.clientId = clientId;
            this.session = session;
            this.signedInAt = signedInAt;
        }

        public long getClientId() {
            return clientId;
        }

        public Session getSession() {
            return session;
        }

        public Instant getSignedInAt() {
            return signedInAt;
        }
    }
}
