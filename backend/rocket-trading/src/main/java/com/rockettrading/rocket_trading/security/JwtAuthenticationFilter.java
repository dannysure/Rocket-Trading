package com.rockettrading.rocket_trading.security;

import com.rockettrading.rocket_trading.model.Session;
import com.rockettrading.rocket_trading.repository.SessionRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final SessionRepository sessionRepository;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   SessionRepository sessionRepository,
                                   AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.sessionRepository = sessionRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedClient authenticatedClient = jwtService.parseToken(header.substring(7));
            Session session = sessionRepository.findBySessionId(authenticatedClient.sessionId());
            if (session == null || !session.isActive()) {
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new BadCredentialsException("Session expired or revoked")
                );
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authenticatedClient,
                    null,
                    List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid bearer token", exception)
            );
        }
    }
}
