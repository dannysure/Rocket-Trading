package com.rockettrading.rocket_trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.finnhub")
public class NewsProperties {
    private String baseUrl = "https://finnhub.io/api/v1";
    private String apiKey = "";
    private long cacheSeconds = 300;
    private int lookbackDays = 7;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public long getCacheSeconds() { return cacheSeconds; }
    public void setCacheSeconds(long cacheSeconds) { this.cacheSeconds = cacheSeconds; }
    public int getLookbackDays() { return lookbackDays; }
    public void setLookbackDays(int lookbackDays) { this.lookbackDays = lookbackDays; }
}
