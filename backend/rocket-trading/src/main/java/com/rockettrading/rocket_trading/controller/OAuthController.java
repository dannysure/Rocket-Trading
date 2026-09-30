package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.model.ClientProfile;
import com.rockettrading.rocket_trading.repository.ClientProfileRepository;
import com.rockettrading.rocket_trading.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth 2.0 Controller
 * 
 * Handles:
 * - OAuth callback endpoints
 * - Token generation and exchange
 * - User profile retrieval
 * - Sign-out/logout
 * 
 * Flow:
 * 1. Frontend redirects to /api/v1/auth/oauth/login/{provider}
 * 2. Backend redirects to OAuth provider
 * 3. OAuth provider redirects back to /api/v1/auth/oauth/callback
 * 4. Backend generates JWT token and redirects to frontend callback
 * 5. Frontend receives token in URL and stores in sessionStorage
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class OAuthController {

  private final ClientProfileRepository clientProfileRepository;
  private final JwtTokenProvider jwtTokenProvider;

  public OAuthController(
    ClientProfileRepository clientProfileRepository,
    JwtTokenProvider jwtTokenProvider
  ) {
    this.clientProfileRepository = clientProfileRepository;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  /**
   * OAuth 2.0 success handler
   * Called after successful OAuth authentication
   * Returns JWT token that frontend can use
   */
  @GetMapping("/oauth/success")
  public Map<String, Object> oauthSuccess(@AuthenticationPrincipal OAuth2User principal) {
    if (principal == null) {
      return error("Authentication failed: principal is null");
    }

    try {
      Integer clientId = (Integer) principal.getAttribute("clientId");
      String email = (String) principal.getAttribute("email");
      String provider = (String) principal.getAttribute("provider");

      if (clientId == null || email == null) {
        return error("Missing required attributes from OAuth provider");
      }

      // Load user from database
      Optional<ClientProfile> user = clientProfileRepository.findById(clientId);
      if (user.isEmpty()) {
        return error("User not found: " + clientId);
      }

      ClientProfile profile = user.get();

      // Generate JWT token
      String token = jwtTokenProvider.generateToken(clientId, email);
      long expiresAt = jwtTokenProvider.getExpirationTime();

      log.info("OAuth success for user {} via provider {}", email, provider);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("clientId", clientId);
      response.put("email", email);
      response.put("name", profile.getName());
      response.put("accessToken", token);
      response.put("tokenType", "Bearer");
      response.put("expiresIn", (expiresAt - System.currentTimeMillis()) / 1000);
      response.put("provider", provider);
      response.put("redirectUrl", "http://localhost:4200/auth/callback?token=" + token);

      return response;
    } catch (Exception ex) {
      log.error("Error in OAuth success handler", ex);
      return error("Failed to process OAuth authentication: " + ex.getMessage());
    }
  }

  /**
   * OAuth 2.0 failure handler
   */
  @GetMapping("/oauth/failure")
  public Map<String, Object> oauthFailure() {
    log.warn("OAuth authentication failed");
    return error("OAuth authentication failed. Please try again.");
  }

  /**
   * Get current authenticated user profile
   * Requires valid JWT token
   */
  @GetMapping("/me")
  public Map<String, Object> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
    if (principal == null) {
      return error("Not authenticated");
    }

    try {
      Integer clientId = (Integer) principal.getAttribute("clientId");
      Optional<ClientProfile> user = clientProfileRepository.findById(clientId);

      if (user.isEmpty()) {
        return error("User not found");
      }

      ClientProfile profile = user.get();
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("clientId", profile.getClientId());
      response.put("name", profile.getName());
      response.put("email", profile.getEmail());
      response.put("riskProfile", profile.getRiskProfile());

      return response;
    } catch (Exception ex) {
      log.error("Error retrieving current user", ex);
      return error("Failed to retrieve user information");
    }
  }

  /**
   * Sign out / Logout
   * Invalidates session
   */
  @PostMapping("/sign-out")
  public Map<String, Object> signOut() {
    // Token invalidation would require a token blacklist in production
    // For now, frontend just deletes the token from sessionStorage
    log.info("User signed out");
    return success("Successfully signed out");
  }

  /**
   * Health check endpoint
   */
  @GetMapping("/health")
  public Map<String, Object> health() {
    return success("OAuth service is healthy");
  }

  /**
   * Helper method for success responses
   */
  private Map<String, Object> success(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("message", message);
    return response;
  }

  /**
   * Helper method for error responses
   */
  private Map<String, Object> error(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", false);
    response.put("error", message);
    return response;
  }
}
