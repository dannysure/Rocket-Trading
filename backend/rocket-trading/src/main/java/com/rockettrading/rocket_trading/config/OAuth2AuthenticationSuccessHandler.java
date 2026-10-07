package com.rockettrading.rocket_trading.config;

import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.repository.SessionRepository;
import com.rockettrading.rocket_trading.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OAuth2 Authentication Success Handler
 * 
 * Called after successful OAuth2 authentication (code exchange)
 * - Extracts user info from OAuth2User
 * - Creates/updates user in database
 * - Generates JWT token
 * - Redirects to frontend with JWT in URL
 */
@Slf4j
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private final ClientRepository clientRepository;
  private final SessionRepository sessionRepository;
  private final JwtService jwtService;
  
  @Value("${app.frontend.url:http://localhost:4200}")
  private String frontendUrl;

  public OAuth2AuthenticationSuccessHandler(
    ClientRepository clientRepository,
    SessionRepository sessionRepository,
    JwtService jwtService
  ) {
    this.clientRepository = clientRepository;
    this.sessionRepository = sessionRepository;
    this.jwtService = jwtService;
  }

  @Override
  public void onAuthenticationSuccess(
    HttpServletRequest request,
    HttpServletResponse response,
    Authentication authentication
  ) throws IOException, ServletException {
    
    try {
      OAuth2User principal = (OAuth2User) authentication.getPrincipal();
      
      if (principal == null) {
        log.error("OAuth2User principal is null");
        redirectWithError(response, "Authentication failed: principal is null");
        return;
      }

      // OAuthUserService has already loaded or created the client and stored its ID
      Long clientId = principal.getAttribute("clientId");
      Client client = clientId != null ? clientRepository.findById(clientId) : null;
      if (client == null) {
        log.error("No client found for OAuth2 principal (clientId={})", clientId);
        redirectWithError(response, "Could not load user account");
        return;
      }

      String email = client.getEmail();
      log.info("OAuth2 success for email: {}", email);

      // Create session
      long sessionId = generatePositiveId();
      Instant expiresAt = Instant.now().plus(Duration.ofHours(8));
      sessionRepository.insert(sessionId, client.getClientId(), expiresAt);

      Session session = new Session(sessionId, expiresAt);

      // Generate JWT token
      String token = jwtService.generateToken(client, session);

      log.info("Generated JWT token for user: {}", email);

      // Redirect to frontend with token in URL
      // Use frontend URL from configuration (supports any port: 4200, 49876, etc.)
      String redirectUrl = frontendUrl + "/auth/callback?token=" + token;
      response.sendRedirect(redirectUrl);

    } catch (Exception ex) {
      log.error("Error in OAuth2 authentication success handler", ex);
      try {
        redirectWithError(response, "Authentication error: " + ex.getMessage());
      } catch (IOException e) {
        log.error("Error sending error redirect", e);
      }
    }
  }

  private void redirectWithError(HttpServletResponse response, String errorMessage) throws IOException {
    // Use frontend URL from configuration
    String redirectUrl = frontendUrl + "/auth/callback?error=" + 
      java.net.URLEncoder.encode(errorMessage, "UTF-8");
    response.sendRedirect(redirectUrl);
  }

  private long generatePositiveId() {
    return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
  }
}
