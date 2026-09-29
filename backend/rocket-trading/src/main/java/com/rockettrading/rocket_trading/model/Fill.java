package com.rockettrading.rocket_trading.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Fill {
    private long fillId;
    private long orderId;
    private BigDecimal executionPrice;
    private long executedQuantity;
    private Instant executedAt;

    public Fill() {
    }

    public Fill(long fillId, long orderId, BigDecimal executionPrice, long executedQuantity, Instant executedAt) {
        setFillId(fillId);
        setOrderId(orderId);
        setExecutionPrice(executionPrice);
        setExecutedQuantity(executedQuantity);
        setExecutedAt(executedAt);
    }

    public long getFillId() {
        return fillId;
    }

    public void setFillId(long fillId) {
        if (fillId <= 0) {
            throw new IllegalArgumentException("fillId must be positive");
        }
        this.fillId = fillId;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        this.orderId = orderId;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(BigDecimal executionPrice) {
        if (executionPrice == null || executionPrice.signum() <= 0) {
            throw new IllegalArgumentException("executionPrice must be a positive decimal");
        }
        this.executionPrice = executionPrice;
    }

    public long getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(long executedQuantity) {
        if (executedQuantity <= 0) {
            throw new IllegalArgumentException("executedQuantity must be positive");
        }
        this.executedQuantity = executedQuantity;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        if (executedAt == null) {
            throw new IllegalArgumentException("executedAt must not be null");
        }
        this.executedAt = executedAt;
    }

    public BigDecimal getTotalValue() {
        return executionPrice.multiply(BigDecimal.valueOf(executedQuantity));
    }

    public BigDecimal getAveragePrice() {
        return executionPrice;
    }
}

