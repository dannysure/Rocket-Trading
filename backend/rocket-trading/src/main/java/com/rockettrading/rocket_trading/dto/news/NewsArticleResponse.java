package com.rockettrading.rocket_trading.dto.news;

import java.time.Instant;
import java.util.List;

public record NewsArticleResponse(
        String id,
        String headline,
        String summary,
        String source,
        String url,
        String imageUrl,
        Instant publishedAt,
        List<String> symbols
) {}
