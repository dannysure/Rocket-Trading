package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.FauxnanceProperties;
import com.rockettrading.rocket_trading.dto.quote.CandleResponse;
import com.rockettrading.rocket_trading.dto.quote.QuoteResponse;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.model.Quote;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuoteService {
    // Display prices may be a few seconds old; order pricing never uses this cache
    private static final Duration DISPLAY_TTL = Duration.ofSeconds(5);
    private static final Duration HISTORY_TTL = Duration.ofMinutes(10);

    private final QuoteProvider quoteProvider;
    private final FauxnanceProperties fauxnanceProperties;
    private final Map<String, Cached<Quote>> displayQuotes = new ConcurrentHashMap<>();
    private final Map<String, Cached<List<CandleResponse>>> histories = new ConcurrentHashMap<>();

    public QuoteService(QuoteProvider quoteProvider, FauxnanceProperties fauxnanceProperties) {
        this.quoteProvider = quoteProvider;
        this.fauxnanceProperties = fauxnanceProperties;
    }

    /** The provider market a symbol is priced in, from its canonical form (X:BTC-USD, FX:EURUSD, AAPL). */
    public static String marketFor(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("X:")) return "crypto";
        if (normalized.startsWith("FX:")) return "fx";
        return "stock";
    }

    public QuoteResponse getQuote(String symbol, String market) {
        String resolvedMarket = market == null || market.isBlank() ? marketFor(symbol) : market.toLowerCase(Locale.ROOT);
        Quote quote = displayQuote(symbol, resolvedMarket);
        return QuoteResponse.from(quote, resolvedMarket, isCurrent(quote, resolvedMarket));
    }

    /** A recent quote for showing to a client; may be served from a few-seconds cache and may be stale. */
    public Quote displayQuote(String symbol, String market) {
        String key = market + "|" + symbol.trim().toUpperCase(Locale.ROOT);
        Cached<Quote> cached = displayQuotes.get(key);
        if (cached != null && cached.isFresh(DISPLAY_TTL)) {
            return cached.value();
        }
        Quote quote = fetchQuote(symbol, market, false);
        displayQuotes.put(key, new Cached<>(quote, Instant.now()));
        return quote;
    }

    /** A quote fit to price an order with: valid, and within the market's freshness window. */
    public Quote fetchCurrentQuote(String symbol, String market) {
        return fetchQuote(symbol, market, true);
    }

    /**
     * Fetches and validates a quote. With {@code requireCurrent}, a quote older than the market's
     * freshness window is refused with QUOTE_STALE (the caller may wait for the market instead).
     */
    public Quote fetchQuote(String symbol, String market, boolean requireCurrent) {
        if (!"stock".equalsIgnoreCase(market) && !"crypto".equalsIgnoreCase(market) && !"fx".equalsIgnoreCase(market)) {
            throw new IllegalArgumentException("market must be stock, crypto or fx");
        }
        Quote quote = quoteProvider.fetchQuote(symbol, market);
        if (!quote.getSymbol().equalsIgnoreCase(symbol) || quote.getAsk().compareTo(quote.getBid()) < 0) {
            throw new ConflictException("QUOTE_INVALID", "Quote symbol or bid/ask is invalid");
        }
        for (var amount : List.of(quote.getBid(), quote.getAsk())) {
            var stored = amount.setScale(4, java.math.RoundingMode.HALF_UP);
            if (stored.signum() <= 0 || stored.precision() > 16) {
                throw new ConflictException("QUOTE_INVALID", "Quote is outside supported price precision");
            }
        }
        if (quote.getCapturedAt().isAfter(Instant.now().plusSeconds(5))) {
            throw new ConflictException("QUOTE_INVALID", "Quote timestamp " + quote.getCapturedAt() + " is in the future");
        }
        if (requireCurrent && !isCurrent(quote, market)) {
            throw new ConflictException("QUOTE_STALE", "Quote timestamp " + quote.getCapturedAt() + " is not current");
        }
        return quote;
    }

    public boolean isCurrent(Quote quote, String market) {
        return quote.isCurrent(Instant.now(), maxAgeSeconds(market));
    }

    public long maxAgeSeconds(String market) {
        return "fx".equalsIgnoreCase(market) ? fauxnanceProperties.getFxMaxAgeSeconds() : fauxnanceProperties.getMaxAgeSeconds();
    }

    public List<CandleResponse> getDailyHistory(String symbol, int days) {
        int boundedDays = Math.max(5, Math.min(days, 365));
        String key = symbol.trim().toUpperCase(Locale.ROOT) + "|" + boundedDays;
        Cached<List<CandleResponse>> cached = histories.get(key);
        if (cached != null && cached.isFresh(HISTORY_TTL)) {
            return cached.value();
        }
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(boundedDays);
        List<CandleResponse> candles = List.copyOf(quoteProvider.fetchDailyCandles(symbol.trim().toUpperCase(Locale.ROOT), from));
        if (!candles.isEmpty()) {
            histories.put(key, new Cached<>(candles, Instant.now()));
        }
        return candles;
    }

    private record Cached<T>(T value, Instant storedAt) {
        boolean isFresh(Duration ttl) {
            return storedAt.plus(ttl).isAfter(Instant.now());
        }
    }
}
