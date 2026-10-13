package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.ops.OpsOrderSummaryResponse;
import com.rockettrading.rocket_trading.dto.ops.OrderTraceResponse;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.OpsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Trading-operations lookups across all clients. Restricted to the OPERATIONS role in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {
    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OpsOrderSummaryResponse>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        AuthenticatedClient staff = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(opsService.search(staff.clientId(), q, status, limit)));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderTraceResponse>> trace(@PathVariable long orderId, Authentication authentication) {
        AuthenticatedClient staff = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(opsService.trace(staff.clientId(), orderId)));
    }
}
