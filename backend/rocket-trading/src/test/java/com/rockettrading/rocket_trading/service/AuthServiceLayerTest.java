package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.auth.RegisterClientRequest;
import com.rockettrading.rocket_trading.dto.auth.SessionResponse;
import com.rockettrading.rocket_trading.dto.auth.SignInRequest;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.exception.UnauthorizedException;
import com.rockettrading.rocket_trading.config.JwtProperties;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.repository.SessionRepository;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.security.JwtService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceLayerTest {

    private JwtService jwtService() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("rocket-trading");
        jwtProperties.setSecret("change-me-development-jwt-secret-1234567890");
        jwtProperties.setExpirationHours(8);
        return new JwtService(jwtProperties);
    }

    @Test
    void registerCreatesClientWhenEmailIsUnused() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        ClientAccountRepository clientAccountRepository = mock(ClientAccountRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        AuthService authService = new AuthService(clientRepository, clientAccountRepository, sessionRepository, jwtService());

        when(clientRepository.findByEmail("joanna@example.com")).thenReturn(null);

        var response = authService.register(new RegisterClientRequest(
                "Joanna",
                "joanna@example.com",
                LocalDate.of(1992, 5, 12),
                "Balanced",
                new BigDecimal("25000.00")
        ));

        assertEquals("Joanna", response.name());
        assertEquals("joanna@example.com", response.email());
        verify(clientRepository).insertProfile(any(Client.class), any(LocalDate.class), any(String.class));
        verify(clientAccountRepository).insert(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        ClientAccountRepository clientAccountRepository = mock(ClientAccountRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        AuthService authService = new AuthService(clientRepository, clientAccountRepository, sessionRepository, jwtService());

        when(clientRepository.findByEmail("joanna@example.com"))
                .thenReturn(new Client(1L, "Joanna", "joanna@example.com"));

        assertThrows(ConflictException.class,
                () -> authService.register(new RegisterClientRequest("Joanna", "joanna@example.com", null, null, null)));
    }

    @Test
    void signInReturnsTokenForFixtureClient() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        ClientAccountRepository clientAccountRepository = mock(ClientAccountRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        JwtService jwtService = jwtService();
        AuthService authService = new AuthService(clientRepository, clientAccountRepository, sessionRepository, jwtService);
        Client client = new Client(7L, "Joanna", "joanna@example.com");

        when(clientRepository.findByEmail("joanna@example.com")).thenReturn(client);

        SessionResponse response = authService.signIn(new SignInRequest("joanna@example.com"));

        assertEquals(7L, response.clientId());
        assertEquals("joanna@example.com",
                jwtService.parseToken(response.accessToken()).email());
        verify(sessionRepository).insert(anyLong(), anyLong(), any(Instant.class));
    }

    @Test
    void signInRejectsUnknownFixtureClient() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        ClientAccountRepository clientAccountRepository = mock(ClientAccountRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        AuthService authService = new AuthService(clientRepository, clientAccountRepository, sessionRepository, jwtService());

        when(clientRepository.findByEmail("missing@example.com")).thenReturn(null);

        assertThrows(UnauthorizedException.class,
                () -> authService.signIn(new SignInRequest("missing@example.com")));
    }

    @Test
    void signOutRevokesExistingSession() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        ClientAccountRepository clientAccountRepository = mock(ClientAccountRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        AuthService authService = new AuthService(clientRepository, clientAccountRepository, sessionRepository, jwtService());
        Session session = new Session(101L, Instant.now().plusSeconds(60));

        when(sessionRepository.findBySessionId(101L)).thenReturn(session);

        assertDoesNotThrow(() -> authService.signOut(new AuthenticatedClient(7L, 101L, "joanna@example.com")));
        verify(sessionRepository).updateExpiresAt(101L, Instant.EPOCH);
    }
}
