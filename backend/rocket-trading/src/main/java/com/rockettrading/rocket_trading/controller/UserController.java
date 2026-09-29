package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.instrument.SupportedInstrumentResponse;
import com.rockettrading.rocket_trading.dto.user.ClientProfileResponse;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ClientProfileResponse>> getProfile(Authentication authentication) {
        AuthenticatedClient authenticatedClient = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(authenticatedClient.clientId())));
    }

    @GetMapping("/instruments")
    public ResponseEntity<ApiResponse<List<SupportedInstrumentResponse>>> listSupportedInstruments() {
        return ResponseEntity.ok(ApiResponse.success(userService.listSupportedInstruments()));
    }
}
