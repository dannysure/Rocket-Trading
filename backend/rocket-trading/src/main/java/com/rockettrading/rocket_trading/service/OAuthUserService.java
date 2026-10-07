package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.repository.model.ClientAccountRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

  private final ClientRepository clientRepository;
  private final ClientAccountRepository clientAccountRepository;

  public OAuthUserService(ClientRepository clientRepository, ClientAccountRepository clientAccountRepository) {
    this.clientRepository = clientRepository;
    this.clientAccountRepository = clientAccountRepository;
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
      // Must be an OAuth2AuthenticationException so Spring routes it to the failure handler instead of a 500
      throw new OAuth2AuthenticationException(
        new OAuth2Error("user_provisioning_failed"),
        "Failed to authenticate via " + userRequest.getClientRegistration().getClientName(),
        ex
      );
    }
  }

  /**
   * Process OAuth user: load or create from database
   */
  private OAuth2User processOAuthUser(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    String email = getEmailFromOAuth2User(oAuth2User, registrationId);
    String name = getNameFromOAuth2User(oAuth2User, registrationId);
    String login = oAuth2User.getAttribute("login");

    // If email is not available (e.g., GitHub user has private email), use login as fallback
    if (email == null || email.isEmpty()) {
      if (login != null && !login.isEmpty()) {
        email = login + "@github.local";
        log.warn("Email not available from GitHub, using login-based email: {}", email);
      } else {
        throw new RuntimeException("Could not extract email or login from " + registrationId + " provider");
      }
    }

    // Use name from OAuth or fallback to login or email prefix
    if (name == null || name.isEmpty()) {
      if (login != null && !login.isEmpty()) {
        name = login;
      } else {
        name = email.split("@")[0];
      }
    }

    log.info("Processing OAuth user: email={}, name={}, provider={}", email, name, registrationId);

    // Find existing user or create new
    Client client = clientRepository.findByEmail(email);

    if (client != null) {
      log.info("User {} already exists, logging in", email);
    } else {
      // client_profiles.client_id has no DB default, so the ID is generated here
      client = new Client(generatePositiveId(), name, email);
      clientRepository.insertProfile(client, null, "Balanced");
      log.info("Created new user from {} provider: {} (clientId={})", registrationId, email, client.getClientId());
    }

    // Portfolio and order endpoints require a DIRECT_TRADING account; also backfills OAuth users created before this existed
    ensureDirectTradingAccount(client.getClientId());

    // Build OAuth2User with mapped attributes
    Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
    attributes.put("clientId", client.getClientId());
    attributes.put("email", email);
    attributes.put("name", client.getName());
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
   * Create the client's DIRECT_TRADING account if it doesn't exist yet
   */
  private void ensureDirectTradingAccount(long clientId) {
    if (clientAccountRepository.findDirectTradingAccountByClientId(clientId) != null) {
      return;
    }
    ClientAccountRecord account = new ClientAccountRecord();
    account.setClientId(clientId);
    account.setAccountType("DIRECT_TRADING");
    account.setCashBalance(new BigDecimal("10000.00"));
    account.setCurrency("USD");
    account.setOpenedDate(LocalDate.now());
    clientAccountRepository.insert(account);
    log.info("Created DIRECT_TRADING account for clientId={}", clientId);
  }

  /**
   * Extract email from OAuth2User attributes (GitHub provider)
   */
  private String getEmailFromOAuth2User(OAuth2User oAuth2User, String registrationId) {
    return oAuth2User.getAttribute("email");
  }

  /**
   * Extract name from OAuth2User attributes (GitHub provider)
   */
  private String getNameFromOAuth2User(OAuth2User oAuth2User, String registrationId) {
    String name = oAuth2User.getAttribute("name");
    return name != null ? name : oAuth2User.getAttribute("login");
  }

  /**
   * Get the attribute name key for GitHub provider
   * Used to identify the principal attribute
   */
  private String getAttributeNameKey(String registrationId) {
    return "id";
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
