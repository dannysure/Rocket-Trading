package com.rockettrading.rocket_trading.model;

import com.rockettrading.rocket_trading.enums.AssetClass;
import lombok.Setter;

public class Instrument {
    private final String symbol;
    private final AssetClass assetClass;
    @Setter
    private boolean tradable;

    public Instrument(String symbol, AssetClass assetClass, boolean tradable) {
        this.symbol = symbol;
        this.assetClass = assetClass;
        this.tradable = tradable;
    }

    public String getSymbol() {
        return symbol;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public boolean isTradable() {
        return tradable;

    }

}

