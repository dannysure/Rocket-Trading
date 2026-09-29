package com.rockettrading.rocket_trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quotes.fauxnance")
public class FauxnanceProperties {
    private String baseUrl;
    private String apiKey;
    private String stockPath;
    private String cryptoPath;
    private long maxAgeSeconds;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getStockPath() {
        return stockPath;
    }

    public void setStockPath(String stockPath) {
        this.stockPath = stockPath;
    }

    public String getCryptoPath() {
        return cryptoPath;
    }

    public void setCryptoPath(String cryptoPath) {
        this.cryptoPath = cryptoPath;
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }
}
