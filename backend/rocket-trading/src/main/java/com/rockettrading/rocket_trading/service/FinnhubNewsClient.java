package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.config.NewsProperties;
import com.rockettrading.rocket_trading.dto.news.NewsArticleResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class FinnhubNewsClient implements NewsProvider {
    private final RestClient restClient;
    private final NewsProperties properties;
    private final ObjectMapper objectMapper;

    public FinnhubNewsClient(@Qualifier("newsRestClient") RestClient newsRestClient, NewsProperties properties, ObjectMapper objectMapper) {
        this.restClient = newsRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NewsArticleResponse> latestMarketNews() {
        if (!StringUtils.hasText(properties.getApiKey())) return List.of();
        try {
            String body = restClient.get().uri(uri -> uri.path("/news")
                    .queryParam("category", "general")
                    .queryParam("token", properties.getApiKey()).build()).retrieve().body(String.class);
            return parse(body);
        } catch (RestClientException | IllegalArgumentException exception) {
            return List.of();
        }
    }

    @Override
    public List<NewsArticleResponse> newsForSymbol(String symbol) {
        if (!StringUtils.hasText(properties.getApiKey()) || !StringUtils.hasText(symbol)) return List.of();
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(Math.max(1, properties.getLookbackDays()));
        try {
            String body = restClient.get().uri(uri -> uri.path("/company-news")
                    .queryParam("symbol", symbol.trim().toUpperCase())
                    .queryParam("from", from)
                    .queryParam("to", to)
                    .queryParam("token", properties.getApiKey()).build()).retrieve().body(String.class);
            return parse(body);
        } catch (RestClientException | IllegalArgumentException exception) {
            return List.of();
        }
    }

    private List<NewsArticleResponse> parse(String body) {
        if (!StringUtils.hasText(body)) return List.of();
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) return List.of();
            List<NewsArticleResponse> articles = new ArrayList<>();
            for (JsonNode item : root) {
                String headline = text(item, "headline");
                String url = text(item, "url");
                if (!StringUtils.hasText(headline) || !StringUtils.hasText(url)) continue;
                Instant published = item.hasNonNull("datetime")
                        ? Instant.ofEpochSecond(item.path("datetime").asLong()) : Instant.EPOCH;
                List<String> symbols = new ArrayList<>();
                item.path("related").asText("").lines().filter(StringUtils::hasText).forEach(symbols::add);
                articles.add(new NewsArticleResponse(
                        text(item, "id"), headline, text(item, "summary"), text(item, "source"), url,
                        text(item, "image"), published, Collections.unmodifiableList(symbols)));
            }
            return List.copyOf(articles);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }
}
