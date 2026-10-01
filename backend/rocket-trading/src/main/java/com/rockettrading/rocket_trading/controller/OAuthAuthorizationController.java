package com.rockettrading.rocket_trading.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * OAuth 2.0 Authorization Endpoint
 * Handles the /api/v1/oauth2/authorization/{provider} requests
 * Redirects to Spring Security's OAuth2 authorization endpoint
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth2")
public class OAuthAuthorizationController {

  private final ClientRegistrationRepository clientRegistrationRepository;

  public OAuthAuthorizationController(ClientRegistrationRepository clientRegistrationRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  /**
   * Initiates OAuth 2.0 authorization flow
   * Redirects to /oauth2/authorization/{registrationId}
   */
  @GetMapping("/authorization/{provider}")
  public void authorize(@PathVariable String provider, HttpServletResponse response) throws IOException {
    log.info("OAuth authorization request for provider: {}", provider);
    
    // Map provider names to registration IDs if needed
    String registrationId = provider.toLowerCase();
    
    // Verify provider is registered
    try {
      clientRegistrationRepository.findByRegistrationId(registrationId);
    } catch (IllegalArgumentException e) {
      log.error("Provider not registered: {}", provider);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.setContentType("application/json");
      response.getWriter().write("{\"error\": \"Provider not registered: " + provider + "\"}");
      return;
    }
    
    // Redirect to Spring Security's OAuth2 authorization endpoint
    String redirectUrl = "/oauth2/authorization/" + registrationId;
    log.info("Redirecting to: {}", redirectUrl);
    response.sendRedirect(redirectUrl);
  }
}
