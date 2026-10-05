package com.rockettrading.rocket_trading.dto.instrument;

import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;

public record SupportedInstrumentResponse(
        long instrumentId,
        String symbol,
        String name,
        String assetClass,
        String baseCurrency,
        boolean tradable,
        String market
) {
    public static SupportedInstrumentResponse from(FinancialInstrumentRecord instrument) {
        String market = "Crypto".equalsIgnoreCase(instrument.getAssetClass()) ? "crypto" : "stock";
        return new SupportedInstrumentResponse(
                instrument.getInstrumentId(),
                instrument.getTickerSymbol(),
                instrument.getInstrumentName(),
                instrument.getAssetClass(),
                instrument.getBaseCurrency(),
                instrument.isTradable(),
                market
        );
    }
}
