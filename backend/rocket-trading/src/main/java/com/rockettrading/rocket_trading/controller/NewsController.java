package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.news.NewsArticleResponse;
import com.rockettrading.rocket_trading.service.NewsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/market")
    public ResponseEntity<ApiResponse<List<NewsArticleResponse>>> marketNews() {
        return ResponseEntity.ok(ApiResponse.success(newsService.marketNews()));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<List<NewsArticleResponse>>> symbolNews(@PathVariable String symbol) {
        return ResponseEntity.ok(ApiResponse.success(newsService.symbolNews(symbol)));
    }
}
