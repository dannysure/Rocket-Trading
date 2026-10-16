package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.perps.*;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.PerpMarketStream;
import com.rockettrading.rocket_trading.service.PerpetualFuturesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/perps")
public class PerpetualFuturesController {
    private final PerpetualFuturesService perpetualFuturesService;
    private final PerpMarketStream marketStream;

    public PerpetualFuturesController(PerpetualFuturesService perpetualFuturesService, PerpMarketStream marketStream) {
        this.perpetualFuturesService = perpetualFuturesService;
        this.marketStream = marketStream;
    }

    @GetMapping("/markets")
    public ResponseEntity<ApiResponse<List<PerpMarketResponse>>> listMarkets() {
        return ResponseEntity.ok(ApiResponse.success(perpetualFuturesService.listMarkets()));
    }

    @GetMapping("/markets/{symbol}/ticker")
    public ResponseEntity<ApiResponse<PerpTickerResponse>> getTicker(@PathVariable String symbol) {
        return ResponseEntity.ok(ApiResponse.success(perpetualFuturesService.getTicker(symbol)));
    }

    /** Spot index candles for the chart; interval is in seconds (60, 300, 900 or 3600). */
    @GetMapping("/markets/{symbol}/candles")
    public ResponseEntity<ApiResponse<List<PerpCandleResponse>>> getCandles(@PathVariable String symbol,
                                                                            @RequestParam(defaultValue = "60") int interval) {
        return ResponseEntity.ok(ApiResponse.success(perpetualFuturesService.getCandles(symbol, interval)));
    }

    /** Live tickers for every market as server-sent events ("tickers", a JSON array), several times a second. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return marketStream.subscribe();
    }

    @GetMapping("/account")
    public ResponseEntity<ApiResponse<PerpAccountResponse>> getAccount(Authentication authentication) {
        AuthenticatedClient client = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(perpetualFuturesService.getAccount(client.clientId())));
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<PerpOrderResponse>> submitOrder(@Valid @RequestBody SubmitPerpOrderRequest request,
                                                                      @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                      Authentication authentication) {
        AuthenticatedClient client = (AuthenticatedClient) authentication.getPrincipal();
        PerpOrderResponse order = perpetualFuturesService.submitOrder(client.clientId(), request, key);
        if ("REJECTED".equals(order.status())) {
            throw new ConflictException("ORDER_REJECTED", order.rejectionReason());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(order));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<PerpOrderResponse>>> listOrders(Authentication authentication) {
        AuthenticatedClient client = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(perpetualFuturesService.listOrders(client.clientId())));
    }

    @GetMapping("/funding")
    public ResponseEntity<ApiResponse<List<PerpFundingResponse>>> listFunding(Authentication authentication) {
        AuthenticatedClient client = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(perpetualFuturesService.listFunding(client.clientId())));
    }
}
