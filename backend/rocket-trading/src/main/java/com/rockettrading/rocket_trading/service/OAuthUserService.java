package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.model.ClientProfile;
import com.rockettrading.rocket_trading.repository.ClientProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth 2.0 User Service
 * 
 * Loads or creates user from OAuth provider response
 * Handles user profile creation and attribute mapping
 * 
 * Flow:
 * 1. OAuth provider authenticates user
 * 2. This service loads user from database OR creates new user
 * 3. Returns OAuth2User with mapped attributes
 */
@Slf4j
@Service
public class OAuthUserService extends DefaultOAuth2UserService {

  private final ClientProfileRepository clientProfileRepository;
  private final JwtTokenService jwtTokenService;

  public OAuthUserService(
    ClientProfileRepository clientProfileRepository,
    JwtTokenService jwtTokenService
  ) {
    this.clientProfileRepository = clientProfileRepository;
    this.jwtTokenService = jwtTokenService;
  }

  /**
   * Load user from OAuth provider
   * Creates user if doesn't exist
   */
  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) {
    // Load OAuth2User from provider
    OAuth2User oAuth2User = super.loadUser(userRequest);

    try {
      return processOAuthUser(userRequest, oAuth2User);
    } catch (Exception ex) {
      log.error("Failed to process OAuth user", ex);
      throw new RuntimeException("Failed to authenticate via " + userRequest.getClientRegistration().getClientName(), ex);
    }
  }

  /**
   * Process OAuth user: load or create from database
   */
  private OAuth2User processOAuthUser(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    String email = getEmailFromOAuth2User(oAuth2User, registrationId);
    String name = getNameFromOAuth2User(oAuth2User, registrationId);

    if (email == null) {
      throw new RuntimeException("Could not extract email from " + registrationId + " provider");
    }

    // Find existing user or create new
    Optional<ClientProfile> existingUser = clientProfileRepository.findByEmail(email);
    ClientProfile user;

    if (existingUser.isPresent()) {
      user = existingUser.get();
      log.info("User {} already exists, logging in", email);
    } else {
      user = createNewUser(email, name);
      log.info("Created new user from {} provider: {}", registrationId, email);
    }

    // Build OAuth2User with mapped attributes
    Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
    attributes.put("clientId", user.getClientId());
    attributes.put("email", email);
    attributes.put("name", name);
    attributes.put("provider", registrationId);

    Collection<? extends GrantedAuthority> authorities = Collections.singletonList(
      new SimpleGrantedAuthority("ROLE_USER")
    );

    return new DefaultOAuth2User(
      authorities,
      attributes,
      getAttributeNameKey(registrationId)
    );
  }

  /**
   * Extract email from OAuth2User attributes
   * Different providers use different attribute names
   */
  private String getEmailFromOAuth2User(OAuth2User oAuth2User, String registrationId) {
    return switch (registrationId) {
      case "google" -> oAuth2User.getAttribute("email");
      case "github" -> oAuth2User.getAttribute("email");
      case "microsoft" -> oAuth2User.getAttribute("mail") != null
        ? oAuth2User.getAttribute("mail")
        : oAuth2User.getAttribute("userPrincipalName");
      default -> oAuth2User.getAttribute("email");
    };
  }

  /**
   * Extract name from OAuth2User attributes
   */
  private String getNameFromOAuth2User(OAuth2User oAuth2User, String registrationId) {
    return switch (registrationId) {
      case "google" -> oAuth2User.getAttribute("name");
      case "github" -> oAuth2User.getAttribute("name") != null
        ? oAuth2User.getAttribute("name")
        : oAuth2User.getAttribute("login");
      case "microsoft" -> oAuth2User.getAttribute("displayName") != null
        ? oAuth2User.getAttribute("displayName")
        : oAuth2User.getAttribute("name");
      default -> oAuth2User.getAttribute("name");
    };
  }

  /**
   * Get the attribute name key for different providers
   * Used to identify the principal attribute
   */
  private String getAttributeNameKey(String registrationId) {
    return switch (registrationId) {
      case "google" -> "sub";
      case "github" -> "id";
      case "microsoft" -> "oid";
      default -> "sub";
    };
  }

  /**
   * Create new user from OAuth authentication
   */
  private ClientProfile createNewUser(String email, String name) {
    ClientProfile newUser = new ClientProfile();
    newUser.setEmail(email);
    newUser.setName(name != null ? name : email.split("@")[0]);
    newUser.setRiskProfile("Balanced"); // Default risk profile
    newUser.setCreatedAt(java.time.LocalDateTime.now());

    // Create account for user
    com.rockettrading.rocket_trading.model.Account account = new com.rockettrading.rocket_trading.model.Account();
    account.setClientProfile(newUser);
    account.setAccountType("Trading");
    account.setCashBalance(BigDecimal.valueOf(100000)); // Default initial balance
    account.setCurrency("USD");
    account.setCreatedAt(YearMonth.now());

    newUser.setDefaultAccount(account);
    return clientProfileRepository.save(newUser);
  }
}
