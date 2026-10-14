package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class WatchlistEntryRecord extends FinancialInstrumentRecord {
    private Instant addedAt;
}
