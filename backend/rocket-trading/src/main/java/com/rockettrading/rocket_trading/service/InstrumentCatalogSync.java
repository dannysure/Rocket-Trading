package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.exception.ExternalServiceException;
import com.rockettrading.rocket_trading.repository.InstrumentRepository;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Keeps every instrument's details (name, type, currency, exchange, tradable) exactly as Fauxnance
 * reports them. The migrations only choose which tickers the platform offers; Fauxnance is the
 * source of truth for what each one is. Tickers Fauxnance does not recognize stop being offered.
 */
@Component
@ConditionalOnProperty(name = "catalog.sync.enabled", havingValue = "true", matchIfMissing = true)
public class InstrumentCatalogSync {
    private static final Logger log = LoggerFactory.getLogger(InstrumentCatalogSync.class);
    // Segments the provider's own type or exchange settles; US-exchange equities keep the platform's grouping
    // (e.g. UK companies offered through their US listings)
    private static final Set<String> PROVIDER_DETERMINED_SEGMENTS = Set.of("IN", "UK", "FX", "CRYPTO");

    private final InstrumentRepository instrumentRepository;
    private final QuoteProvider quoteProvider;

    public InstrumentCatalogSync(InstrumentRepository instrumentRepository, QuoteProvider quoteProvider) {
        this.instrumentRepository = instrumentRepository;
        this.quoteProvider = quoteProvider;
    }

    @Scheduled(fixedDelayString = "${catalog.sync-ms:21600000}", initialDelay = 0)
    public void scheduledSync() {
        try {
            Result result = syncAll();
            log.info("Instrument catalogue synced from Fauxnance: {} updated, {} no longer offered, {} skipped",
                    result.updated(), result.withdrawn(), result.skipped());
        } catch (RuntimeException exception) {
            log.warn("Instrument catalogue sync failed; keeping the current catalogue", exception);
        }
    }

    public Result syncAll() {
        int updated = 0;
        int withdrawn = 0;
        int skipped = 0;
        for (FinancialInstrumentRecord current : instrumentRepository.findAll()) {
            FinancialInstrumentRecord fromProvider;
            try {
                fromProvider = quoteProvider.lookupInstrument(current.getTickerSymbol());
            } catch (ExternalServiceException unavailable) {
                // Provider down or not configured: no answer is not the same as "unknown symbol"
                skipped++;
                continue;
            }
            if (fromProvider == null) {
                if (current.isTradable()) {
                    instrumentRepository.markNotTradable(current.getInstrumentId());
                    withdrawn++;
                }
                continue;
            }
            fromProvider.setInstrumentId(current.getInstrumentId());
            if (!PROVIDER_DETERMINED_SEGMENTS.contains(fromProvider.getMarketSegment())) {
                fromProvider.setMarketSegment(current.getMarketSegment());
            }
            if (!sameDetails(current, fromProvider)) {
                instrumentRepository.updateFromProvider(fromProvider);
                updated++;
            }
        }
        return new Result(updated, withdrawn, skipped);
    }

    private boolean sameDetails(FinancialInstrumentRecord a, FinancialInstrumentRecord b) {
        return Objects.equals(a.getInstrumentName(), b.getInstrumentName())
                && Objects.equals(a.getAssetClass(), b.getAssetClass())
                && Objects.equals(a.getBaseCurrency(), b.getBaseCurrency())
                && a.isTradable() == b.isTradable()
                && Objects.equals(a.getListingExchange(), b.getListingExchange())
                && Objects.equals(a.getMarketSegment(), b.getMarketSegment());
    }

    public record Result(int updated, int withdrawn, int skipped) {
    }
}
