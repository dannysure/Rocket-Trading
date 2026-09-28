package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.order.FillResponse;
import com.rockettrading.rocket_trading.dto.order.OrderResponse;
import com.rockettrading.rocket_trading.dto.order.SubmitOrderRequest;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> submitOrder(@Valid @RequestBody SubmitOrderRequest request,
                                                                  Authentication authentication) {
        AuthenticatedClient authenticatedClient = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(orderService.submitOrder(authenticatedClient.clientId(), request)));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> listOrders(Authentication authentication) {
        AuthenticatedClient authenticatedClient = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(orderService.listOrders(authenticatedClient.clientId())));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable long orderId,
                                                               Authentication authentication) {
        AuthenticatedClient authenticatedClient = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(authenticatedClient.clientId(), orderId)));
    }

    @GetMapping("/fills/{orderId}")
    public ResponseEntity<ApiResponse<List<FillResponse>>> listFills(@PathVariable long orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.listFills(orderId)));
    }
}
