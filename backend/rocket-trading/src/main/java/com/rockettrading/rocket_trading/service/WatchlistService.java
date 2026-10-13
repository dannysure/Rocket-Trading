package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.watchlist.WatchlistItemResponse;
import com.rockettrading.rocket_trading.exception.NotFoundException;
import com.rockettrading.rocket_trading.repository.InstrumentRepository;
import com.rockettrading.rocket_trading.repository.WatchlistRepository;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WatchlistService {
    static final int MAX_ENTRIES = 50;

    private final WatchlistRepository watchlistRepository;
    private final InstrumentRepository instrumentRepository;

    public WatchlistService(WatchlistRepository watchlistRepository, InstrumentRepository instrumentRepository) {
        this.watchlistRepository = watchlistRepository;
        this.instrumentRepository = instrumentRepository;
    }

    public List<WatchlistItemResponse> list(long clientId) {
        return watchlistRepository.findByClientId(clientId).stream().map(WatchlistItemResponse::from).toList();
    }

    public List<WatchlistItemResponse> add(long clientId, String symbol) {
        FinancialInstrumentRecord instrument = requireInstrument(symbol);
        if (watchlistRepository.findByClientId(clientId).size() >= MAX_ENTRIES) {
            throw new IllegalArgumentException("A watchlist can hold up to " + MAX_ENTRIES + " instruments");
        }
        watchlistRepository.add(clientId, instrument.getInstrumentId());
        return list(clientId);
    }

    public List<WatchlistItemResponse> remove(long clientId, String symbol) {
        watchlistRepository.remove(clientId, requireInstrument(symbol).getInstrumentId());
        return list(clientId);
    }

    private FinancialInstrumentRecord requireInstrument(String symbol) {
        FinancialInstrumentRecord instrument = instrumentRepository.findByTicker(symbol.trim());
        if (instrument == null) {
            throw new NotFoundException("INSTRUMENT_NOT_FOUND", "That instrument is not in the catalogue");
        }
        return instrument;
    }
}
