package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.portfolio.PortfolioSummaryResponse;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {
    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PortfolioSummaryResponse>> getPortfolioSummary(Authentication authentication) {
        AuthenticatedClient authenticatedClient = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(portfolioService.getPortfolioSummary(authenticatedClient.clientId())));
    }
}
