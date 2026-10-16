package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.config.PerpsProperties;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.PerpMarketRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live spot prices from Coinbase Exchange's public WebSocket ticker channel (no API key, no quota).
 * Keeps the latest top of book per product and reconnects whenever the connection drops or goes quiet.
 */
@Component
@ConditionalOnProperty(name = "trading.perps.price-feed.enabled", havingValue = "true", matchIfMissing = true)
public class CoinbaseTickerFeed {
    private static final Logger log = LoggerFactory.getLogger(CoinbaseTickerFeed.class);
    // Heartbeats arrive every second per product, so this much silence means the connection is dead
    private static final Duration SILENCE_LIMIT = Duration.ofSeconds(20);
    private static final long WATCHDOG_SECONDS = 5;
    // A connection attempt that has not finished by then is abandoned and retried
    private static final Duration CONNECT_LIMIT = Duration.ofSeconds(30);

    private final PerpMarketRepository marketRepository;
    private final PerpsProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Received> latest = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "coinbase-ticker-feed");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile WebSocket socket;
    private volatile Instant lastMessageAt = Instant.EPOCH;
    private volatile Instant connectingSince;
    // Identifies the newest connection attempt, so a late one that was abandoned is discarded
    private final AtomicLong attempts = new AtomicLong();
    private volatile boolean warned;

    @Autowired
    public CoinbaseTickerFeed(PerpMarketRepository marketRepository, PerpsProperties properties, ObjectMapper objectMapper) {
        this(marketRepository, properties, objectMapper, Clock.systemUTC());
    }

    CoinbaseTickerFeed(PerpMarketRepository marketRepository, PerpsProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.marketRepository = marketRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        executor.scheduleWithFixedDelay(this::ensureConnected, 0, WATCHDOG_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
        WebSocket current = socket;
        if (current != null) current.abort();
    }

    /** The latest streamed quote for an underlying (e.g. X:BTC-USD), if one arrived within the configured max age. */
    public Optional<Quote> latest(String underlyingSymbol) {
        String product = CoinbaseCandleClient.productId(underlyingSymbol);
        Received received = product == null ? null : latest.get(product);
        if (received == null || received.at().plus(properties.getPriceFeed().maxAge()).isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(received.quote());
    }

    private void ensureConnected() {
        try {
            Instant now = clock.instant();
            boolean quiet = lastMessageAt.plus(SILENCE_LIMIT).isBefore(now);
            if (connectingSince != null && connectingSince.plus(CONNECT_LIMIT).isAfter(now)) return;
            if (socket != null && !quiet) return;
            List<String> products = marketRepository.findActive().stream()
                    .map(market -> CoinbaseCandleClient.productId(market.getUnderlyingSymbol()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (products.isEmpty()) return;
            WebSocket previous = socket;
            socket = null;
            if (previous != null) previous.abort();
            long attempt = attempts.incrementAndGet();
            connectingSince = now;
            lastMessageAt = now;
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(properties.getPriceFeed().getWebsocketUrl()), new Listener())
                    .thenCompose(ws -> ws.sendText(subscription(products), true))
                    .whenComplete((ws, error) -> {
                        // An attempt abandoned for taking too long must not replace a newer connection
                        if (attempts.get() != attempt) {
                            if (ws != null) ws.abort();
                            return;
                        }
                        connectingSince = null;
                        if (error != null) {
                            warnOnce("Coinbase price feed connection failed: " + error.getMessage());
                        } else {
                            socket = ws;
                            log.info("Coinbase price feed subscribed to {}", products);
                        }
                    });
        } catch (RuntimeException exception) {
            connectingSince = null;
            warnOnce("Coinbase price feed could not start: " + exception.getMessage());
        }
    }

    private String subscription(List<String> products) {
        return objectMapper.valueToTree(Map.of("type", "subscribe", "product_ids", products,
                "channels", List.of("ticker", "heartbeat"))).toString();
    }

    /** Applies one feed message; package-private for tests. */
    void handle(String message) {
        lastMessageAt = clock.instant();
        try {
            JsonNode node = objectMapper.readTree(message);
            String type = node.path("type").asText();
            if ("error".equals(type)) {
                warnOnce("Coinbase price feed error: " + node.path("message").asText() + " " + node.path("reason").asText());
                return;
            }
            if (!"ticker".equals(type)) return;
            String product = node.path("product_id").asText();
            Quote quote = new Quote("X:" + product,
                    new BigDecimal(node.path("best_bid").asText()),
                    new BigDecimal(node.path("best_ask").asText()),
                    new BigDecimal(node.path("price").asText()),
                    0, 0,
                    node.hasNonNull("time") ? Instant.parse(node.get("time").asText()) : clock.instant());
            if (quote.getAsk().compareTo(quote.getBid()) < 0) return;
            latest.put(product, new Received(quote, clock.instant()));
            warned = false;
        } catch (Exception malformed) {
            log.debug("Ignoring malformed Coinbase message: {}", malformed.getMessage());
        }
    }

    // Logs the first failure of an outage instead of one per reconnect attempt
    private void warnOnce(String message) {
        if (!warned) log.warn("{}. Perps fall back to Fauxnance prices until it recovers.", message);
        warned = true;
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                handle(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (socket == webSocket) socket = null;
            warnOnce("Coinbase price feed closed (" + statusCode + " " + reason + ")");
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (socket == webSocket) socket = null;
            warnOnce("Coinbase price feed failed: " + error.getMessage());
        }
    }

    private record Received(Quote quote, Instant at) {
    }
}
