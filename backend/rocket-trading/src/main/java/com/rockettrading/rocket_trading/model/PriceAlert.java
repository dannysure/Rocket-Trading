package com.rockettrading.rocket_trading.model;

import java.math.BigDecimal;
import java.time.Instant;

public class PriceAlert {
    private long alertId;
    private String symbol;
    private BigDecimal targetPrice;
    private String direction;
    private String status;
    private Instant createdAt;
    private Instant triggeredAt;
    private BigDecimal priceAtTrigger;

    public PriceAlert() {
    }

    public PriceAlert(long alertId, String symbol, BigDecimal targetPrice, String direction, 
                     String status, Instant createdAt) {
        setAlertId(alertId);
        setSymbol(symbol);
        setTargetPrice(targetPrice);
        setDirection(direction);
        setStatus(status);
        setCreatedAt(createdAt);
    }

    public long getAlertId() {
        return alertId;
    }

    public void setAlertId(long alertId) {
        if (alertId <= 0) {
            throw new IllegalArgumentException("alertId must be positive");
        }
        this.alertId = alertId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        this.symbol = symbol.trim().toUpperCase();
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        if (targetPrice == null || targetPrice.signum() <= 0) {
            throw new IllegalArgumentException("targetPrice must be a positive decimal");
        }
        this.targetPrice = targetPrice;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        if (direction == null || direction.trim().isEmpty()) {
            throw new IllegalArgumentException("direction must not be blank");
        }
        String trimmedDirection = direction.trim().toUpperCase();
        if (!trimmedDirection.equals("ABOVE") && !trimmedDirection.equals("BELOW")) {
            throw new IllegalArgumentException("direction must be ABOVE or BELOW");
        }
        this.direction = trimmedDirection;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        String trimmedStatus = status.trim().toUpperCase();
        if (!trimmedStatus.equals("ACTIVE") && !trimmedStatus.equals("TRIGGERED") && 
            !trimmedStatus.equals("DISMISSED") && !trimmedStatus.equals("EXPIRED")) {
            throw new IllegalArgumentException("status must be ACTIVE, TRIGGERED, DISMISSED, or EXPIRED");
        }
        this.status = trimmedStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        this.createdAt = createdAt;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        if (triggeredAt != null && createdAt != null && triggeredAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("triggeredAt cannot be before createdAt");
        }
        this.triggeredAt = triggeredAt;
    }

    public BigDecimal getPriceAtTrigger() {
        return priceAtTrigger;
    }

    public void setPriceAtTrigger(BigDecimal priceAtTrigger) {
        if (priceAtTrigger == null || priceAtTrigger.signum() <= 0) {
            throw new IllegalArgumentException("priceAtTrigger must be a positive decimal");
        }
        this.priceAtTrigger = priceAtTrigger;
    }

    public boolean evaluateAgainstQuote(BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.signum() <= 0) {
            throw new IllegalArgumentException("currentPrice must be a positive decimal");
        }
        if (!status.equals("ACTIVE")) {
            return false;
        }

        boolean isTriggered = false;
        if (direction.equals("ABOVE")) {
            isTriggered = currentPrice.compareTo(targetPrice) >= 0;
        } else if (direction.equals("BELOW")) {
            isTriggered = currentPrice.compareTo(targetPrice) <= 0;
        }

        return isTriggered;
    }

    public void trigger(BigDecimal currentPrice, Instant triggeredAt) {
        if (currentPrice == null || currentPrice.signum() <= 0) {
            throw new IllegalArgumentException("currentPrice must be a positive decimal");
        }
        if (triggeredAt == null) {
            throw new IllegalArgumentException("triggeredAt must not be null");
        }
        if (!status.equals("ACTIVE")) {
            throw new IllegalStateException("only ACTIVE alerts can be triggered");
        }

        this.status = "TRIGGERED";
        this.priceAtTrigger = currentPrice;
        this.triggeredAt = triggeredAt;
    }

    public void dismiss() {
        if (!status.equals("ACTIVE")) {
            throw new IllegalStateException("only ACTIVE alerts can be dismissed");
        }
        this.status = "DISMISSED";
    }

    public void expire() {
        if (status.equals("TRIGGERED")) {
            throw new IllegalStateException("triggered alerts cannot be expired");
        }
        this.status = "EXPIRED";
    }

    public boolean isActive() {
        return status.equals("ACTIVE");
    }

    public boolean isTriggered() {
        return status.equals("TRIGGERED");
    }

    public boolean isDismissed() {
        return status.equals("DISMISSED");
    }

    public boolean isExpired() {
        return status.equals("EXPIRED");
    }
}

