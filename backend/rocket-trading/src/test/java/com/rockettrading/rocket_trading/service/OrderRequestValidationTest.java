package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.order.SubmitOrderRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class OrderRequestValidationTest {
    @Test void rejectsMissingNegativeAndOverpreciseQuantity() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (BigDecimal quantity : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("0.0000001")}) {
                assertFalse(factory.getValidator().validate(new SubmitOrderRequest("AAPL", "BUY", quantity, "stock", "MARKET", null)).isEmpty());
            }
        }
    }
    @Test void requiresLimitPriceOnlyForLimitOrders() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new SubmitOrderRequest("AAPL", "BUY", BigDecimal.ONE, "stock", "LIMIT", null)).isEmpty());
            assertFalse(validator.validate(new SubmitOrderRequest("AAPL", "BUY", BigDecimal.ONE, "stock", "MARKET", BigDecimal.TEN)).isEmpty());
            assertTrue(validator.validate(new SubmitOrderRequest("AAPL", "BUY", BigDecimal.ONE, "stock", "LIMIT", BigDecimal.TEN)).isEmpty());
        }
    }
    @Test void normalizesEquivalentRequestsForRetries() {
        var first = new SubmitOrderRequest(" aapl ", "buy", new BigDecimal("1.0"), null, null, null);
        var second = new SubmitOrderRequest("AAPL", "BUY", new BigDecimal("1.000000"), "stock", "MARKET", null);
        assertEquals(first.fingerprint(), second.fingerprint());
    }
}
