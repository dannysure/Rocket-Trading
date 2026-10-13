package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.watchlist.WatchlistItemResponse;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.WatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {
    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WatchlistItemResponse>>> list(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(watchlistService.list(clientId(authentication))));
    }

    @PutMapping("/{symbol}")
    public ResponseEntity<ApiResponse<List<WatchlistItemResponse>>> add(@PathVariable String symbol, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(watchlistService.add(clientId(authentication), symbol)));
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<ApiResponse<List<WatchlistItemResponse>>> remove(@PathVariable String symbol, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(watchlistService.remove(clientId(authentication), symbol)));
    }

    private long clientId(Authentication authentication) {
        return ((AuthenticatedClient) authentication.getPrincipal()).clientId();
    }
}
