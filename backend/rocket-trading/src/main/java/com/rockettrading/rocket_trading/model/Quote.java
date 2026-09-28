package com.rockettrading.rocket_trading.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public class Quote {
    private String symbol;
    private BigDecimal bid;
    private BigDecimal ask;
    private BigDecimal price;
    private long bidVolume;
    private long askVolume;
    private Instant capturedAt;

    public Quote() {
    }

    public Quote(String symbol, BigDecimal bid, BigDecimal ask, BigDecimal price, 
                 long bidVolume, long askVolume, Instant capturedAt) {
        setSymbol(symbol);
        setBid(bid);
        setAsk(ask);
        setPrice(price);
        setBidVolume(bidVolume);
        setAskVolume(askVolume);
        setCapturedAt(capturedAt);
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

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        if (bid == null || bid.signum() <= 0) {
            throw new IllegalArgumentException("bid must be a positive decimal");
        }
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        if (ask == null || ask.signum() <= 0) {
            throw new IllegalArgumentException("ask must be a positive decimal");
        }
        this.ask = ask;
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

    public long getBidVolume() {
        return bidVolume;
    }

    public void setBidVolume(long bidVolume) {
        if (bidVolume < 0) {
            throw new IllegalArgumentException("bidVolume must be non-negative");
        }
        this.bidVolume = bidVolume;
    }

    public long getAskVolume() {
        return askVolume;
    }

    public void setAskVolume(long askVolume) {
        if (askVolume < 0) {
            throw new IllegalArgumentException("askVolume must be non-negative");
        }
        this.askVolume = askVolume;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        if (capturedAt == null) {
            throw new IllegalArgumentException("capturedAt must not be null");
        }
        this.capturedAt = capturedAt;
    }

    public BigDecimal getSpread() {
        if (bid == null || ask == null) {
            throw new IllegalStateException("bid and ask must be set to calculate spread");
        }
        return ask.subtract(bid);
    }

    public BigDecimal getSpreadPercentage() {
        if (bid == null || ask == null || bid.signum() == 0) {
            throw new IllegalStateException("bid and ask must be set to calculate spread percentage");
        }
        BigDecimal spread = ask.subtract(bid);
        return spread.divide(bid, 6, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
    }

    public boolean isCurrent(Instant asOf, long maxAgeSeconds) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf must not be null");
        }
        if (maxAgeSeconds <= 0) {
            throw new IllegalArgumentException("maxAgeSeconds must be positive");
        }
        Instant maxAge = capturedAt.plus(Duration.ofSeconds(maxAgeSeconds));
        return asOf.isBefore(maxAge) || asOf.equals(maxAge);
    }

    public long getAgeInSeconds(Instant asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf must not be null");
        }
        if (asOf.isBefore(capturedAt)) {
            throw new IllegalArgumentException("asOf cannot be before capturedAt");
        }
        return Duration.between(capturedAt, asOf).getSeconds();
    }

    public boolean isStale(Instant asOf, long maxAgeSeconds) {
        return !isCurrent(asOf, maxAgeSeconds);
    }
}

