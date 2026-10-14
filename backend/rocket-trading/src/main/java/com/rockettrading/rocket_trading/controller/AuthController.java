package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.auth.ClientRegistrationResponse;
import com.rockettrading.rocket_trading.dto.auth.OAuthLoginResponse;
import com.rockettrading.rocket_trading.dto.auth.RegisterClientRequest;
import com.rockettrading.rocket_trading.dto.auth.SessionResponse;
import com.rockettrading.rocket_trading.dto.auth.SignInRequest;
import com.rockettrading.rocket_trading.dto.auth.UpdateProfileRequest;
import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final ObjectProvider<ClientRegistrationRepository> oauthRegistrations;

    public AuthController(AuthService authService, ObjectProvider<ClientRegistrationRepository> oauthRegistrations) {
        this.authService = authService;
        this.oauthRegistrations = oauthRegistrations;
    }

    /** Which sign-in methods this deployment offers, so the sign-in page only shows working ones. */
    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> providers() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "password", true,
                "github", oauthRegistrations.getIfAvailable() != null
        )));
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

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ClientRegistrationResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        AuthenticatedClient authenticatedClient = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(authService.updateProfile(
                authenticatedClient.clientId(),
                request
        )));
    }

    /**
     * Handle OAuth callback from frontend
     * Frontend passes JWT token and this endpoint validates it and returns user info
     */
    @PostMapping("/oauth/success")
    public ResponseEntity<ApiResponse<OAuthLoginResponse>> handleOAuthSuccess(
            Authentication authentication
    ) {
        AuthenticatedClient authenticatedClient = (AuthenticatedClient) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(authService.getOAuthUserInfo(authenticatedClient)));
    }
}
