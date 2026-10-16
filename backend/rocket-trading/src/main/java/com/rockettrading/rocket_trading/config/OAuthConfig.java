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
 * - GitHub
 * - Google
 * 
 * Environment variables (each provider is offered only when both credentials are set;
 * email and password sign-in works either way):
 * - OAUTH_GITHUB_CLIENT_ID
 * - OAUTH_GITHUB_CLIENT_SECRET
 * - OAUTH_GOOGLE_CLIENT_ID
 * - OAUTH_GOOGLE_CLIENT_SECRET
 */
@Configuration
public class OAuthConfig {

  @Value("${oauth.github.client-id:}")
  private String githubClientId;

  @Value("${oauth.github.client-secret:}")
  private String githubClientSecret;

  @Value("${oauth.github.redirect-uri:http://localhost:8081/login/oauth2/code/github}")
  private String githubRedirectUri;

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
  @ConditionalOnExpression("(!'${oauth.github.client-id:}'.isBlank() and !'${oauth.github.client-secret:}'.isBlank()) or (!'${oauth.google.client-id:}'.isBlank() and !'${oauth.google.client-secret:}'.isBlank())")
  public InMemoryClientRegistrationRepository clientRegistrationRepository() {
    List<ClientRegistration> registrations = new ArrayList<>();
    if (!githubClientId.isBlank() && !githubClientSecret.isBlank()) {
      registrations.add(githubClientRegistration());
    }
    if (!googleClientId.isBlank() && !googleClientSecret.isBlank()) {
      registrations.add(googleClientRegistration());
    }
    return new InMemoryClientRegistrationRepository(registrations.toArray(new ClientRegistration[0]));
  }

  /**
   * GitHub OAuth 2.0 Provider Configuration
   */
  private ClientRegistration githubClientRegistration() {
    return ClientRegistration.withRegistrationId("github")
      .clientId(githubClientId)
      .clientSecret(githubClientSecret)
      .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_POST)
      .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri(githubRedirectUri)
      .authorizationUri("https://github.com/login/oauth/authorize")
      .tokenUri("https://github.com/login/oauth/access_token")
      .userInfoUri("https://api.github.com/user")
      .userNameAttributeName("login")
      .clientName("GitHub")
      .scope("read:user", "user:email")
      .build();
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
      .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
      .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
      .userNameAttributeName("sub")
      .clientName("Google")
      .scope("openid", "profile", "email")
      .build();
  }
}
