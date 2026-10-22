package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.config.PerpsProperties;
import com.rockettrading.rocket_trading.dto.perps.PerpTickerResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pushes every market's ticker to subscribed clients as server-sent events: on a steady tick for price moves, and
 * straight after any fill or liquidation commits so open interest, premium and funding update the moment a trade
 * lands. One snapshot is built per push and shared by all subscribers, on a thread of its own so a slow client
 * never delays order or settlement work.
 */
@Component
public class PerpMarketStream {
    private static final Logger log = LoggerFactory.getLogger(PerpMarketStream.class);
    // Longer than the page's 10-minute live session; the client reconnects if it is still live
    private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofMinutes(15);

    private final PerpetualFuturesService service;
    private final ObjectMapper objectMapper;
    private final PerpsProperties properties;
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "perps-stream");
        thread.setDaemon(true);
        return thread;
    });

    public PerpMarketStream(PerpetualFuturesService service, ObjectMapper objectMapper, PerpsProperties properties) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        long interval = properties.getStreamIntervalMs();
        executor.scheduleAtFixedRate(this::broadcast, interval, interval, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
        subscribers.forEach(SseEmitter::complete);
    }

    /** A new subscription; it receives the current tickers straight away, then one update per stream interval. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SUBSCRIPTION_TIMEOUT.toMillis());
        emitter.onCompletion(() -> subscribers.remove(emitter));
        // Completing on timeout ends the response cleanly instead of raising AsyncRequestTimeoutException
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> subscribers.remove(emitter));
        String snapshot = snapshot();
        if (snapshot != null && !send(emitter, snapshot)) return emitter;
        subscribers.add(emitter);
        return emitter;
    }

    /** A trade changed open interest: push it now rather than on the next tick. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarketChanged(PerpMarketChanged change) {
        if (subscribers.isEmpty() || executor.isShutdown()) return;
        try {
            executor.execute(this::broadcast);
        } catch (RejectedExecutionException shuttingDown) {
            // The application is stopping; there is no one left to tell
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }

    void broadcast() {
        if (subscribers.isEmpty()) return;
        String snapshot = snapshot();
        if (snapshot == null) return;
        for (SseEmitter emitter : subscribers) {
            if (!send(emitter, snapshot)) subscribers.remove(emitter);
        }
    }

    private String snapshot() {
        try {
            List<PerpTickerResponse> tickers = service.listTickers();
            return tickers.isEmpty() ? null : objectMapper.writeValueAsString(tickers);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Perps stream snapshot failed: {}", exception.getMessage());
            return null;
        }
    }

    private boolean send(SseEmitter emitter, String snapshot) {
        try {
            emitter.send(SseEmitter.event().name("tickers").data(snapshot, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException disconnected) {
            return false;
        }
    }
}
