package com.rockettrading.rocket_trading.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FillTest {

    private Fill fill;
    private final long fillId = 501L;
    private final long orderId = 101L;
    private final BigDecimal executionPrice = new BigDecimal("150.25");
    private final long executedQuantity = 100L;
    private final Instant executedAt = Instant.parse("2026-09-23T15:30:00Z");

    @BeforeEach
    void setUp() {
        fill = new Fill(fillId, orderId, executionPrice, executedQuantity, executedAt);
    }

    @Test
    @DisplayName("constructor stores all fill details")
    void constructorStoresAllFillDetails() {
        assertAll(
                () -> assertEquals(fillId, fill.getFillId()),
                () -> assertEquals(orderId, fill.getOrderId()),
                () -> assertEquals(executionPrice, fill.getExecutionPrice()),
                () -> assertEquals(executedQuantity, fill.getExecutedQuantity()),
                () -> assertEquals(executedAt, fill.getExecutedAt())
        );
    }

    @Test
    @DisplayName("getTotalValue returns execution price times executed quantity")
    void getTotalValueReturnsExecutionPriceTimesExecutedQuantity() {
        BigDecimal expectedTotal = new BigDecimal("150.25").multiply(BigDecimal.valueOf(100L));

        assertEquals(expectedTotal, fill.getTotalValue());
    }

    @Test
    @DisplayName("getAveragePrice returns execution price")
    void getAveragePriceReturnsExecutionPrice() {
        assertEquals(executionPrice, fill.getAveragePrice());
    }

    @Test
    @DisplayName("setters update fill details")
    void settersUpdateFillDetails() {
        BigDecimal newPrice = new BigDecimal("155.50");
        long newQuantity = 50L;
        long newOrderId = 102L;

        fill.setExecutionPrice(newPrice);
        fill.setExecutedQuantity(newQuantity);
        fill.setOrderId(newOrderId);

        assertAll(
                () -> assertEquals(newPrice, fill.getExecutionPrice()),
                () -> assertEquals(newQuantity, fill.getExecutedQuantity()),
                () -> assertEquals(newOrderId, fill.getOrderId())
        );
    }

    @Test
    @DisplayName("constructor rejects non-positive fill id")
    void constructorRejectsNonPositiveFillId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Fill(0L, orderId, executionPrice, executedQuantity, executedAt)
        );

        assertEquals("fillId must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects non-positive order id")
    void constructorRejectsNonPositiveOrderId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Fill(fillId, -1L, executionPrice, executedQuantity, executedAt)
        );

        assertEquals("orderId must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects zero execution price")
    void constructorRejectsZeroExecutionPrice() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Fill(fillId, orderId, BigDecimal.ZERO, executedQuantity, executedAt)
        );

        assertEquals("executionPrice must be a positive decimal", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects negative execution price")
    void constructorRejectsNegativeExecutionPrice() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Fill(fillId, orderId, new BigDecimal("-50.00"), executedQuantity, executedAt)
        );

        assertEquals("executionPrice must be a positive decimal", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects null execution price")
    void constructorRejectsNullExecutionPrice() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Fill(fillId, orderId, null, executedQuantity, executedAt)
        );

        assertEquals("executionPrice must be a positive decimal", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects zero executed quantity")
    void constructorRejectsZeroExecutedQuantity() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Fill(fillId, orderId, executionPrice, 0L, executedAt)
        );

        assertEquals("executedQuantity must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects negative executed quantity")
    void constructorRejectsNegativeExecutedQuantity() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Fill(fillId, orderId, executionPrice, -50L, executedAt)
        );

        assertEquals("executedQuantity must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects null executed at")
    void constructorRejectsNullExecutedAt() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Fill(fillId, orderId, executionPrice, executedQuantity, null)
        );

        assertEquals("executedAt must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("setExecutionPrice rejects non-positive price")
    void setExecutionPriceRejectsNonPositivePrice() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fill.setExecutionPrice(new BigDecimal("-10.00"))
        );

        assertEquals("executionPrice must be a positive decimal", exception.getMessage());
    }

    @Test
    @DisplayName("setExecutedQuantity rejects non-positive quantity")
    void setExecutedQuantityRejectsNonPositiveQuantity() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fill.setExecutedQuantity(0L)
        );

        assertEquals("executedQuantity must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("setExecutedAt rejects null timestamp")
    void setExecutedAtRejectsNullTimestamp() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fill.setExecutedAt(null)
        );

        assertEquals("executedAt must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("getTotalValue calculates correctly with fractional prices")
    void getTotalValueCalculatesCorrectlyWithFractionalPrices() {
        Fill fractionalFill = new Fill(
                501L,
                101L,
                new BigDecimal("123.456"),
                789L,
                executedAt
        );

        BigDecimal expectedTotal = new BigDecimal("123.456").multiply(BigDecimal.valueOf(789L));

        assertEquals(expectedTotal, fractionalFill.getTotalValue());
    }

    @Test
    @DisplayName("multiple fills with different prices and quantities calculate independently")
    void multiplePartialFillsCalculateCorrectly() {
        Fill fill1 = new Fill(501L, 101L, new BigDecimal("100.00"), 50L, executedAt);
        Fill fill2 = new Fill(502L, 101L, new BigDecimal("101.00"), 50L, executedAt.plusSeconds(60));

        assertAll(
                () -> assertEquals(new BigDecimal("5000.00"), fill1.getTotalValue()),
                () -> assertEquals(new BigDecimal("5050.00"), fill2.getTotalValue())
        );
    }
}
