package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.auth.ClientRegistrationResponse;
import com.rockettrading.rocket_trading.dto.auth.RegisterClientRequest;
import com.rockettrading.rocket_trading.dto.auth.SessionResponse;
import com.rockettrading.rocket_trading.dto.auth.SignInRequest;
import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<ClientRegistrationResponse>> register(
            @Valid @RequestBody RegisterClientRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/sign-in")
    public ResponseEntity<ApiResponse<SessionResponse>> signIn(@Valid @RequestBody SignInRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.signIn(request)));
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(Authentication authentication) {
        AuthenticatedClient authenticatedClient = (AuthenticatedClient) authentication.getPrincipal();
        authService.signOut(authenticatedClient);
        return ResponseEntity.noContent().build();
    }
}
