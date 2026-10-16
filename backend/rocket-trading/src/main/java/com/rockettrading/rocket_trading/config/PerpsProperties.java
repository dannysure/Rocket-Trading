package com.rockettrading.rocket_trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "trading.perps")
public class PerpsProperties {
    // Funding is charged at the start of every interval (aligned to the Unix epoch, so 60 is on the hour, UTC)
    private int fundingIntervalMinutes = 60;
    // How often live prices are pushed to clients on the perps stream
    private long streamIntervalMs = 500;
    // Fallback (Fauxnance) quotes and failures are reused this long so a stream outage cannot exhaust its quota
    private long fallbackCacheMs = 10_000;
    private final PriceFeed priceFeed = new PriceFeed();

    public Duration fundingInterval() {
        return Duration.ofMinutes(fundingIntervalMinutes);
    }

    public int getFundingIntervalMinutes() {
        return fundingIntervalMinutes;
    }

    public void setFundingIntervalMinutes(int fundingIntervalMinutes) {
        if (fundingIntervalMinutes < 1 || fundingIntervalMinutes > 1440) {
            throw new IllegalArgumentException("trading.perps.funding-interval-minutes must be between 1 and 1440");
        }
        this.fundingIntervalMinutes = fundingIntervalMinutes;
    }

    public long getStreamIntervalMs() {
        return streamIntervalMs;
    }

    public void setStreamIntervalMs(long streamIntervalMs) {
        if (streamIntervalMs < 100) throw new IllegalArgumentException("trading.perps.stream-interval-ms must be at least 100");
        this.streamIntervalMs = streamIntervalMs;
    }

    public long getFallbackCacheMs() {
        return fallbackCacheMs;
    }

    public void setFallbackCacheMs(long fallbackCacheMs) {
        if (fallbackCacheMs < 0) throw new IllegalArgumentException("trading.perps.fallback-cache-ms must not be negative");
        this.fallbackCacheMs = fallbackCacheMs;
    }

    public PriceFeed getPriceFeed() {
        return priceFeed;
    }

    /** Coinbase Exchange public market data: the spot index for crypto perps. */
    public static class PriceFeed {
        private boolean enabled = true;
        private String websocketUrl = "wss://ws-feed.exchange.coinbase.com";
        private String restUrl = "https://api.exchange.coinbase.com";
        // A streamed price older than this is not used; pricing falls back to Fauxnance
        private long maxAgeSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWebsocketUrl() {
            return websocketUrl;
        }

        public void setWebsocketUrl(String websocketUrl) {
            this.websocketUrl = websocketUrl;
        }

        public String getRestUrl() {
            return restUrl;
        }

        public void setRestUrl(String restUrl) {
            this.restUrl = restUrl;
        }

        public Duration maxAge() {
            return Duration.ofSeconds(maxAgeSeconds);
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            if (maxAgeSeconds < 1) throw new IllegalArgumentException("trading.perps.price-feed.max-age-seconds must be positive");
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }
}
