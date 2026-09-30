package com.rockettrading.rocket_trading.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * OAuth 2.0 Configuration for Spring Boot
 * 
 * Supports multiple OAuth providers:
 * - Google
 * - GitHub
 * - Microsoft (Azure AD)
 * 
 * Environment variables required:
 * - OAUTH_GOOGLE_CLIENT_ID
 * - OAUTH_GOOGLE_CLIENT_SECRET
 * - OAUTH_GITHUB_CLIENT_ID
 * - OAUTH_GITHUB_CLIENT_SECRET
 * - OAUTH_MICROSOFT_CLIENT_ID
 * - OAUTH_MICROSOFT_CLIENT_SECRET
 * - OAUTH_REDIRECT_URI
 */
@Configuration
@EnableWebSecurity
public class OAuthConfig {

  @Value("${oauth.google.client-id:}")
  private String googleClientId;

  @Value("${oauth.google.client-secret:}")
  private String googleClientSecret;

  @Value("${oauth.github.client-id:}")
  private String githubClientId;

  @Value("${oauth.github.client-secret:}")
  private String githubClientSecret;

  @Value("${oauth.microsoft.client-id:}")
  private String microsoftClientId;

  @Value("${oauth.microsoft.client-secret:}")
  private String microsoftClientSecret;

  @Value("${oauth.redirect-uri:http://localhost:4200/auth/callback}")
  private String redirectUri;

  @Value("${server.servlet.context-path:/}")
  private String contextPath;

  /**
   * Configure security filter chain for OAuth 2.0
   * 
   * - Public endpoints: /auth/login, /auth/callback
   * - Protected endpoints: /api/v1/*, /ws
   * - Stateless session (JWT tokens)
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf().disable()
      .cors().and()
      .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
      .authorizeHttpRequests(authz -> authz
        .requestMatchers("/api/v1/auth/login").permitAll()
        .requestMatchers("/api/v1/auth/callback").permitAll()
        .requestMatchers("/api/v1/auth/oauth/**").permitAll()
        .requestMatchers("/health").permitAll()
        .requestMatchers("/api/v1/**").authenticated()
        .requestMatchers("/ws/**").authenticated()
        .anyRequest().permitAll()
      )
      .oauth2Login(oauth2 -> oauth2
        .authorizationEndpoint(endpoint -> endpoint
          .baseUri("/api/v1/auth/oauth2/authorization")
        )
        .redirectionEndpoint(endpoint -> endpoint
          .baseUri("/api/v1/auth/oauth2/callback")
        )
        .defaultSuccessUrl("/api/v1/auth/oauth/success", true)
        .failureUrl("/api/v1/auth/oauth/failure")
      )
      .logout(logout -> logout
        .logoutUrl("/api/v1/auth/sign-out")
        .logoutSuccessUrl("/")
        .permitAll()
      );

    return http.build();
  }

  /**
   * CORS Configuration
   * Allows frontend to call backend from different origin
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(
      "http://localhost:4200",
      "http://localhost:3000",
      "http://127.0.0.1:4200"
    ));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * Password encoder (for future use if password auth is re-introduced)
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * OAuth 2.0 Client Registration Repository
   * In-memory registration of OAuth providers
   */
  @Bean
  public InMemoryClientRegistrationRepository clientRegistrationRepository() {
    return new InMemoryClientRegistrationRepository(
      googleClientRegistration(),
      githubClientRegistration(),
      microsoftClientRegistration()
    );
  }

  /**
   * Google OAuth 2.0 Configuration
   */
  private ClientRegistration googleClientRegistration() {
    return ClientRegistrations.fromIssuerLocation("https://accounts.google.com")
      .clientId(googleClientId)
      .clientSecret(googleClientSecret)
      .redirectUri(redirectUri + "?provider=google")
      .scope("openid", "profile", "email")
      .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
      .build();
  }

  /**
   * GitHub OAuth 2.0 Configuration
   */
  private ClientRegistration githubClientRegistration() {
    return ClientRegistration.withRegistrationId("github")
      .clientId(githubClientId)
      .clientSecret(githubClientSecret)
      .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.BASIC)
      .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri(redirectUri + "?provider=github")
      .authorizationUri("https://github.com/login/oauth/authorize")
      .tokenUri("https://github.com/login/oauth/access_token")
      .userInfoUri("https://api.github.com/user")
      .userNameAttributeName("login")
      .jwkSetUri(null)
      .clientName("GitHub")
      .scope("read:user", "user:email")
      .build();
  }

  /**
   * Microsoft/Azure AD OAuth 2.0 Configuration
   */
  private ClientRegistration microsoftClientRegistration() {
    return ClientRegistrations.fromIssuerLocation("https://login.microsoftonline.com/common/v2.0")
      .clientId(microsoftClientId)
      .clientSecret(microsoftClientSecret)
      .redirectUri(redirectUri + "?provider=microsoft")
      .scope("openid", "profile", "email")
      .build();
  }
}
