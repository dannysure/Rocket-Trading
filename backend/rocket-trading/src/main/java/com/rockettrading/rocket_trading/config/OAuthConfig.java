package com.rockettrading.rocket_trading.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
 * Environment variables (GitHub sign-in is offered only when both are set;
 * email and password sign-in works either way):
 * - OAUTH_GITHUB_CLIENT_ID
 * - OAUTH_GITHUB_CLIENT_SECRET
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
   * OAuth 2.0 Client Registration Repository, registered only when GitHub credentials are configured
   */
  @Bean
  @ConditionalOnExpression("!'${oauth.github.client-id:}'.isBlank() and !'${oauth.github.client-secret:}'.isBlank()")
  public InMemoryClientRegistrationRepository clientRegistrationRepository() {
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
