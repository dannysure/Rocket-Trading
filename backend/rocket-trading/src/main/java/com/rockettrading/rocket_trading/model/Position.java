package com.rockettrading.rocket_trading.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Position {
    private String symbol;
    private long quantity;
    private BigDecimal averageCost;
    private BigDecimal currentPrice;
    private long totalCostBasis;

    public Position() {
    }

    public Position(String symbol, long quantity, BigDecimal averageCost) {
        setSymbol(symbol);
        setQuantity(quantity);
        setAverageCost(averageCost);
        this.totalCostBasis = quantity;
    }

    public Position(String symbol, long quantity, BigDecimal averageCost, BigDecimal currentPrice) {
        setSymbol(symbol);
        setQuantity(quantity);
        setAverageCost(averageCost);
        setCurrentPrice(currentPrice);
        this.totalCostBasis = quantity;
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

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be non-negative");
        }
        this.quantity = quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        if (averageCost == null || averageCost.signum() <= 0) {
            throw new IllegalArgumentException("averageCost must be a positive decimal");
        }
        this.averageCost = averageCost;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.signum() <= 0) {
            throw new IllegalArgumentException("currentPrice must be a positive decimal");
        }
        this.currentPrice = currentPrice;
    }

    public long getTotalCostBasis() {
        return totalCostBasis;
    }

    public void setTotalCostBasis(long totalCostBasis) {
        if (totalCostBasis < 0) {
            throw new IllegalArgumentException("totalCostBasis must be non-negative");
        }
        this.totalCostBasis = totalCostBasis;
    }

    public BigDecimal getPositionValue() {
        if (currentPrice == null) {
            throw new IllegalStateException("currentPrice must be set to calculate position value");
        }
        return currentPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getCostBasisValue() {
        if (averageCost == null) {
            throw new IllegalStateException("averageCost must be set to calculate cost basis");
        }
        return averageCost.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getUnrealizedGainLoss() {
        if (currentPrice == null || averageCost == null) {
            throw new IllegalStateException("currentPrice and averageCost must be set for gain/loss calculation");
        }
        BigDecimal positionValue = getPositionValue();
        BigDecimal costBasis = getCostBasisValue();
        return positionValue.subtract(costBasis);
    }

    public BigDecimal getUnrealizedGainLossPercentage() {
        if (currentPrice == null || averageCost == null || quantity == 0) {
            throw new IllegalStateException("currentPrice, averageCost, and quantity must be set for percentage calculation");
        }
        BigDecimal costBasis = getCostBasisValue();
        if (costBasis.signum() == 0) {
            throw new IllegalStateException("cost basis must be non-zero for percentage calculation");
        }
        BigDecimal gainLoss = getUnrealizedGainLoss();
        return gainLoss.divide(costBasis, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
    }

    public void applyFill(long fillQuantity, BigDecimal fillPrice) {
        if (fillQuantity <= 0) {
            throw new IllegalArgumentException("fillQuantity must be positive");
        }
        if (fillPrice == null || fillPrice.signum() <= 0) {
            throw new IllegalArgumentException("fillPrice must be a positive decimal");
        }

        long newQuantity = quantity + fillQuantity;
        BigDecimal currentCostBasis = averageCost != null ? averageCost.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
        BigDecimal fillCostBasis = fillPrice.multiply(BigDecimal.valueOf(fillQuantity));
        BigDecimal newTotalCost = currentCostBasis.add(fillCostBasis);
        BigDecimal newAverageCost = newTotalCost.divide(BigDecimal.valueOf(newQuantity), 8, RoundingMode.HALF_UP);

        this.quantity = newQuantity;
        this.averageCost = newAverageCost;
        this.totalCostBasis = newQuantity;
    }

    public void reduceFill(long reduceQuantity, BigDecimal exitPrice) {
        if (reduceQuantity <= 0) {
            throw new IllegalArgumentException("reduceQuantity must be positive");
        }
        if (reduceQuantity > quantity) {
            throw new IllegalArgumentException("cannot reduce by more than current quantity");
        }
        if (exitPrice == null || exitPrice.signum() <= 0) {
            throw new IllegalArgumentException("exitPrice must be a positive decimal");
        }

        long newQuantity = quantity - reduceQuantity;
        // When reducing, average cost stays the same for remaining shares
        this.quantity = newQuantity;
        this.totalCostBasis = newQuantity;
    }

    public boolean isEmpty() {
        return quantity == 0;
    }

    public boolean isOpen() {
        return quantity > 0;
    }
}

