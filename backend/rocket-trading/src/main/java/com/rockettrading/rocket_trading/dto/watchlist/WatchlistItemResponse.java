package com.rockettrading.rocket_trading.dto.watchlist;

import com.rockettrading.rocket_trading.dto.instrument.SupportedInstrumentResponse;
import com.rockettrading.rocket_trading.repository.model.WatchlistEntryRecord;

import java.time.Instant;

public record WatchlistItemResponse(SupportedInstrumentResponse instrument, Instant addedAt) {
    public static WatchlistItemResponse from(WatchlistEntryRecord entry) {
        return new WatchlistItemResponse(SupportedInstrumentResponse.from(entry), entry.getAddedAt());
    }
}
