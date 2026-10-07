package com.rockettrading.rocket_trading.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * OAuth 2.0 Provider Configuration
 * 
 * Registers OAuth 2.0 provider:
 * - GitHub
 * 
 * Environment variables:
 * - OAUTH_GITHUB_CLIENT_ID (required)
 * - OAUTH_GITHUB_CLIENT_SECRET (required)
 * - OAUTH_REDIRECT_URI (optional, defaults to http://localhost:4200/auth/callback)
 */
@Configuration
public class OAuthConfig {

  @Value("${oauth.github.client-id:}")
  private String githubClientId;

  @Value("${oauth.github.client-secret:}")
  private String githubClientSecret;

  @Value("${oauth.redirect-uri:http://localhost:8081/login/oauth2/code/github}")
  private String redirectUri;

  /**
   * OAuth 2.0 Client Registration Repository
   * In-memory registration of configured OAuth providers
   * Requires OAUTH_GITHUB_CLIENT_ID and OAUTH_GITHUB_CLIENT_SECRET to be set
   */
  @Bean
  public InMemoryClientRegistrationRepository clientRegistrationRepository() {
    // GitHub OAuth is required for application to function
    if (githubClientId.isEmpty() || githubClientSecret.isEmpty()) {
      throw new IllegalStateException(
        "GitHub OAuth credentials are required. Set OAUTH_GITHUB_CLIENT_ID and OAUTH_GITHUB_CLIENT_SECRET"
      );
    }
    
    return new InMemoryClientRegistrationRepository(githubClientRegistration());
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
      .redirectUri(redirectUri)
      .authorizationUri("https://github.com/login/oauth/authorize")
      .tokenUri("https://github.com/login/oauth/access_token")
      .userInfoUri("https://api.github.com/user")
      .userNameAttributeName("login")
      .clientName("GitHub")
      .scope("read:user", "user:email")
      .build();
  }
}
