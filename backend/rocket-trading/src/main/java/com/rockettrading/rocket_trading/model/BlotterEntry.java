package com.rockettrading.rocket_trading.model;

import java.math.BigDecimal;
import java.time.Instant;

public class BlotterEntry {
    private long entryId;
    private long orderId;
    private String symbol;
    private String side;
    private long quantity;
    private BigDecimal price;
    private String displayStatus;
    private Instant recordedAt;
    private Instant lastUpdatedAt;

    public BlotterEntry() {
    }

    public BlotterEntry(long entryId, long orderId, String symbol, String side, long quantity, 
                        BigDecimal price, String displayStatus, Instant recordedAt, Instant lastUpdatedAt) {
        setEntryId(entryId);
        setOrderId(orderId);
        setSymbol(symbol);
        setSide(side);
        setQuantity(quantity);
        setPrice(price);
        setDisplayStatus(displayStatus);
        setRecordedAt(recordedAt);
        setLastUpdatedAt(lastUpdatedAt);
    }

    public long getEntryId() {
        return entryId;
    }

    public void setEntryId(long entryId) {
        if (entryId <= 0) {
            throw new IllegalArgumentException("entryId must be positive");
        }
        this.entryId = entryId;
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

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        this.symbol = symbol.trim().toUpperCase();
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        if (side == null || side.trim().isEmpty()) {
            throw new IllegalArgumentException("side must not be blank");
        }
        String trimmedSide = side.trim().toUpperCase();
        if (!trimmedSide.equals("BUY") && !trimmedSide.equals("SELL")) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }
        this.side = trimmedSide;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be a positive decimal");
        }
        this.price = price;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }

    public void setDisplayStatus(String displayStatus) {
        if (displayStatus == null || displayStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("displayStatus must not be blank");
        }
        this.displayStatus = displayStatus.trim();
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        if (recordedAt == null) {
            throw new IllegalArgumentException("recordedAt must not be null");
        }
        this.recordedAt = recordedAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Instant lastUpdatedAt) {
        if (lastUpdatedAt == null) {
            throw new IllegalArgumentException("lastUpdatedAt must not be null");
        }
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public BigDecimal getTotalValue() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}

