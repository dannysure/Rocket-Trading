package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.news.NewsArticleResponse;

import java.util.List;

public interface NewsProvider {
    List<NewsArticleResponse> latestMarketNews();
    List<NewsArticleResponse> newsForSymbol(String symbol);
}
