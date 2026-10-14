package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.order.IndicativePriceResponse;
import com.rockettrading.rocket_trading.dto.quote.CandleResponse;
import com.rockettrading.rocket_trading.dto.quote.QuoteResponse;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.OrderService;
import com.rockettrading.rocket_trading.service.QuoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {
    private final QuoteService quoteService;
    private final OrderService orderService;

    public QuoteController(QuoteService quoteService, OrderService orderService) {
        this.quoteService = quoteService;
        this.orderService = orderService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<QuoteResponse>> getQuote(
            @PathVariable String symbol,
            @RequestParam(required = false) String market
    ) {
        return ResponseEntity.ok(ApiResponse.success(quoteService.getQuote(symbol, market)));
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<ApiResponse<List<CandleResponse>>> getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(ApiResponse.success(quoteService.getDailyHistory(symbol, days)));
    }

    /** The price and account-currency total an order would get right now, before it is placed. */
    @GetMapping("/{symbol}/indicative")
    public ResponseEntity<ApiResponse<IndicativePriceResponse>> getIndicativePrice(
            @PathVariable String symbol,
            @RequestParam String side,
            @RequestParam BigDecimal quantity,
            Authentication authentication
    ) {
        AuthenticatedClient client = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(orderService.indicativePrice(
                client.clientId(), symbol, side.trim().toUpperCase(Locale.ROOT), quantity)));
    }
}
