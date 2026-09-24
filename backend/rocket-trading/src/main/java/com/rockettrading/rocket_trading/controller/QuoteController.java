package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.quote.QuoteResponse;
import com.rockettrading.rocket_trading.service.QuoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {
    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<QuoteResponse>> getQuote(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "stock") String market
    ) {
        return ResponseEntity.ok(ApiResponse.success(quoteService.getQuote(symbol, market)));
    }
}
