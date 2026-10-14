package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

@Data
public class FinancialInstrumentRecord {
    private Long instrumentId;
    private String tickerSymbol;
    private String instrumentName;
    private String assetClass;
    private String baseCurrency;
    private boolean tradable;
    // US, UK, IN, FX or CRYPTO: the market a client browses the instrument under
    private String marketSegment;
    private String listingExchange;

    /** Quote-provider market used to price this instrument. */
    public String market() {
        if ("Crypto".equals(assetClass)) return "crypto";
        if ("FX".equals(assetClass)) return "fx";
        return "stock";
    }
}
