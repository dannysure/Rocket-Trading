package com.rockettrading.rocket_trading.model;

public class Instrument {
    private String symbol;
    private String assetClass;
    private boolean tradable;

    public Instrument() {
    }

    public Instrument(String symbol, String assetClass, boolean tradable) {
        setSymbol(symbol);
        setAssetClass(assetClass);
        setTradable(tradable);
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

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        if (assetClass == null || assetClass.trim().isEmpty()) {
            throw new IllegalArgumentException("assetClass must not be blank");
        }
        this.assetClass = assetClass.trim().toUpperCase();
    }

    public boolean isTradable() {
        return tradable;
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instrument that = (Instrument) o;
        return symbol != null && symbol.equals(that.symbol);
    }

    @Override
    public int hashCode() {
        return symbol != null ? symbol.hashCode() : 0;
    }
}

