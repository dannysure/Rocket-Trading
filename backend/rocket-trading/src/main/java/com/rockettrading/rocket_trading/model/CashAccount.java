package com.rockettrading.rocket_trading.model;

import java.math.BigDecimal;

public class CashAccount {
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private BigDecimal totalBalance;

    public CashAccount() {
        this.availableBalance = BigDecimal.ZERO;
        this.reservedBalance = BigDecimal.ZERO;
        this.totalBalance = BigDecimal.ZERO;
    }

    public CashAccount(BigDecimal totalBalance) {
        setTotalBalance(totalBalance);
        this.availableBalance = totalBalance;
        this.reservedBalance = BigDecimal.ZERO;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        if (availableBalance == null || availableBalance.signum() < 0) {
            throw new IllegalArgumentException("availableBalance must be a non-negative decimal");
        }
        this.availableBalance = availableBalance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public void setReservedBalance(BigDecimal reservedBalance) {
        if (reservedBalance == null || reservedBalance.signum() < 0) {
            throw new IllegalArgumentException("reservedBalance must be a non-negative decimal");
        }
        this.reservedBalance = reservedBalance;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        if (totalBalance == null || totalBalance.signum() < 0) {
            throw new IllegalArgumentException("totalBalance must be a non-negative decimal");
        }
        this.totalBalance = totalBalance;
    }

    public void reserveForOrder(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be a positive decimal");
        }
        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("insufficient available balance");
        }
        this.availableBalance = this.availableBalance.subtract(amount);
        this.reservedBalance = this.reservedBalance.add(amount);
    }

    public void settleFill(BigDecimal fillCost) {
        if (fillCost == null || fillCost.signum() <= 0) {
            throw new IllegalArgumentException("fillCost must be a positive decimal");
        }
        if (reservedBalance.compareTo(fillCost) < 0) {
            throw new IllegalArgumentException("insufficient reserved balance for settlement");
        }
        this.reservedBalance = this.reservedBalance.subtract(fillCost);
        this.totalBalance = this.totalBalance.subtract(fillCost);
    }

    public void releaseReservedFunds(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be a positive decimal");
        }
        if (reservedBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("insufficient reserved balance to release");
        }
        this.reservedBalance = this.reservedBalance.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
    }

    public void depositFunds(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("deposit amount must be positive");
        }
        this.availableBalance = this.availableBalance.add(amount);
        this.totalBalance = this.totalBalance.add(amount);
    }

    public void withdrawFunds(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("withdrawal amount must be positive");
        }
        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("insufficient available balance for withdrawal");
        }
        this.availableBalance = this.availableBalance.subtract(amount);
        this.totalBalance = this.totalBalance.subtract(amount);
    }

    public boolean canCoverOrder(BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.signum() <= 0) {
            throw new IllegalArgumentException("orderAmount must be a positive decimal");
        }
        return availableBalance.compareTo(orderAmount) >= 0;
    }
}

