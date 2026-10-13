package com.rockettrading.rocket_trading.security;

import com.rockettrading.rocket_trading.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows password guessing: after repeated failures an email is locked out for a while.
 * In-memory, so it resets on restart and is per instance; a shared store is needed when scaled out.
 */
@Component
public class LoginAttemptGuard {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String email) {
        Attempts current = attempts.get(key(email));
        if (current != null && current.failures() >= MAX_FAILURES && current.windowStart().plus(WINDOW).isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many sign-in attempts. Please wait 15 minutes and try again.");
        }
    }

    public void recordFailure(String email) {
        attempts.compute(key(email), (k, current) -> {
            Instant now = Instant.now();
            if (current == null || current.windowStart().plus(WINDOW).isBefore(now)) {
                return new Attempts(1, now);
            }
            return new Attempts(current.failures() + 1, current.windowStart());
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempts(int failures, Instant windowStart) {
    }
}
