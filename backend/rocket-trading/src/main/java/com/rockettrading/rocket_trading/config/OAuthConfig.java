package com.rockettrading.rocket_trading.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * OAuth 2.0 Provider Configuration
 * 
 * Registers OAuth 2.0 providers when their credentials are configured:
 * - Google
 * 
 * Environment variables (each provider is offered only when both credentials are set;
 * email and password sign-in works either way):
 * - OAUTH_GOOGLE_CLIENT_ID
 * - OAUTH_GOOGLE_CLIENT_SECRET
 */
@Configuration
public class OAuthConfig {

  @Value("${oauth.google.client-id:}")
  private String googleClientId;

  @Value("${oauth.google.client-secret:}")
  private String googleClientSecret;

  @Value("${oauth.google.redirect-uri:http://localhost:8081/login/oauth2/code/google}")
  private String googleRedirectUri;

  /**
   * OAuth 2.0 Client Registration Repository, registered when at least one provider is configured
   */
  @Bean
  @ConditionalOnExpression("!'${oauth.google.client-id:}'.isBlank() and !'${oauth.google.client-secret:}'.isBlank()")
  public InMemoryClientRegistrationRepository clientRegistrationRepository() {
    List<ClientRegistration> registrations = new ArrayList<>();
    if (!googleClientId.isBlank() && !googleClientSecret.isBlank()) {
      registrations.add(googleClientRegistration());
    }
    return new InMemoryClientRegistrationRepository(registrations.toArray(new ClientRegistration[0]));
  }

  private ClientRegistration googleClientRegistration() {
    return ClientRegistration.withRegistrationId("google")
      .clientId(googleClientId)
      .clientSecret(googleClientSecret)
      .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri(googleRedirectUri)
      .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
      .tokenUri("https://oauth2.googleapis.com/token")
      .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
      .userNameAttributeName("sub")
      .clientName("Google")
      .scope("profile", "email")
      .build();
  }
}
