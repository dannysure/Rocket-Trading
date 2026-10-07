package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.repository.SessionRepository;
import com.rockettrading.rocket_trading.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

  private final ClientRepository clientRepository;
  private final SessionRepository sessionRepository;
  private final JwtService jwtService;

  public OAuthController(
    ClientRepository clientRepository,
    SessionRepository sessionRepository,
    JwtService jwtService
  ) {
    this.clientRepository = clientRepository;
    this.sessionRepository = sessionRepository;
    this.jwtService = jwtService;
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
      String email = (String) principal.getAttribute("email");
      String provider = (String) principal.getAttribute("provider");

      if (email == null) {
        return error("Missing email from OAuth provider");
      }

      // Load user from database or create new
      Client client = clientRepository.findByEmail(email);
      if (client == null) {
        String name = (String) principal.getAttribute("name");
        if (name == null) {
          name = email.split("@")[0];
        }
        // client_profiles.client_id has no DB default, so the ID is generated here
        client = new Client(generatePositiveId(), name, email);
        clientRepository.insertProfile(client, null, "Balanced");
        log.info("Created new user from {} provider: {} (clientId={})", provider, email, client.getClientId());
      } else {
        log.info("User {} already exists, logging in", email);
      }

      // Create session for the client
      long sessionId = generatePositiveId();
      Instant expiresAt = Instant.now().plus(Duration.ofHours(8));
      sessionRepository.insert(sessionId, client.getClientId(), expiresAt);

      Session session = new Session(sessionId, expiresAt);

      // Generate JWT token
      String token = jwtService.generateToken(client, session);

      log.info("OAuth success for user {} via provider {}", email, provider);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("clientId", client.getClientId());
      response.put("email", email);
      response.put("name", client.getName());
      response.put("accessToken", token);
      response.put("tokenType", "Bearer");
      response.put("expiresIn", 28800); // 8 hours in seconds
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
      String email = (String) principal.getAttribute("email");
      Client client = clientRepository.findByEmail(email);

      if (client == null) {
        return error("User not found");
      }

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("clientId", client.getClientId());
      response.put("name", client.getName());
      response.put("email", client.getEmail());

      return response;
    } catch (Exception ex) {
      log.error("Error retrieving current user", ex);
      return error("Failed to retrieve user information");
    }
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

  /**
   * Generate a positive random ID
   */
  private long generatePositiveId() {
    long id;
    do {
      id = ThreadLocalRandom.current().nextLong();
    } while (id <= 0);
    return id;
  }
}
