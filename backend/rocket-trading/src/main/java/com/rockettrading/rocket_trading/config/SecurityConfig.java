package com.rockettrading.rocket_trading.config;

import com.rockettrading.rocket_trading.dto.common.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.security.JwtAuthenticationFilter;
import com.rockettrading.rocket_trading.security.RestAuthenticationEntryPoint;
import com.rockettrading.rocket_trading.service.OAuthUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

        @Value("${app.frontend.url:http://localhost:4200}")
        private String frontendUrl;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                                            AccessDeniedHandler accessDeniedHandler,
                                            OAuth2AuthenticationSuccessHandler oauth2SuccessHandler,
                                            OAuthUserService oauthUserService,
                                            ObjectProvider<ClientRegistrationRepository> oauthRegistrations,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/sign-in").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/providers").permitAll()
                        .requestMatchers("/api/v1/oauth2/authorization/**").permitAll()
                        .requestMatchers("/oauth2/authorization/**").permitAll()
                        .requestMatchers("/login/oauth2/code/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        // 9.3: internal access is limited to what each role needs
                        .requestMatchers("/api/v1/reporting/**").hasAnyRole("ANALYST", "OPERATIONS")
                        .requestMatchers("/api/v1/ops/**").hasRole("OPERATIONS")
                        // Trading and personal financial data are for clients acting on their own account
                        .requestMatchers("/api/v1/orders/**", "/api/v1/fills/**", "/api/v1/portfolio/**",
                                "/api/v1/watchlist/**", "/api/v1/quotes/*/indicative").hasRole("CLIENT")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // OAuth sign-in is only wired up when at least one provider is configured
        if (oauthRegistrations.getIfAvailable() != null) {
            httpSecurity.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo.userService(oauthUserService))
                    .successHandler(oauth2SuccessHandler)
                    .failureHandler(oauthFailureHandler()));
        }

        return httpSecurity.build();
    }

        private AuthenticationFailureHandler oauthFailureHandler() {
                return (request, response, exception) -> {
                        String message = exception.getMessage() != null ? exception.getMessage() : "OAuth sign-in failed";
                        String redirectUrl = frontendUrl + "/auth/callback?error="
                                        + URLEncoder.encode(message, StandardCharsets.UTF_8);
                        response.sendRedirect(redirectUrl);
                };
        }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiErrorResponse.of("FORBIDDEN", "Your role does not have access to this resource"));
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                frontendUrl,
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "http://localhost:49876",
                "http://127.0.0.1:49876"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
