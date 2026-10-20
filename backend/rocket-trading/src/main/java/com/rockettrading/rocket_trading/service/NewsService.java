package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.NewsProperties;
import com.rockettrading.rocket_trading.dto.news.NewsArticleResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NewsService {
    private final NewsProvider provider;
    private final NewsProperties properties;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public NewsService(NewsProvider provider, NewsProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    public List<NewsArticleResponse> marketNews() {
        return cached("market", provider::latestMarketNews);
    }

    public List<NewsArticleResponse> symbolNews(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return List.of();
        return cached("symbol:" + normalized, () -> provider.newsForSymbol(normalized));
    }

    private List<NewsArticleResponse> cached(String key, java.util.function.Supplier<List<NewsArticleResponse>> loader) {
        Cached current = cache.get(key);
        if (current != null && current.expiresAt().isAfter(Instant.now())) return current.articles();
        List<NewsArticleResponse> articles = List.copyOf(loader.get());
        cache.put(key, new Cached(articles, Instant.now().plusSeconds(Math.max(30, properties.getCacheSeconds()))));
        return articles;
    }

    private record Cached(List<NewsArticleResponse> articles, Instant expiresAt) {}
}
