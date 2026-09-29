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
}
