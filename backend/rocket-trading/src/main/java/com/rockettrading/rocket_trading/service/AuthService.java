package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.auth.ClientRegistrationResponse;
import com.rockettrading.rocket_trading.dto.auth.RegisterClientRequest;
import com.rockettrading.rocket_trading.dto.auth.SessionResponse;
import com.rockettrading.rocket_trading.dto.auth.SignInRequest;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.exception.UnauthorizedException;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.repository.SessionRepository;
import com.rockettrading.rocket_trading.repository.model.ClientAccountRecord;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {
    private final ClientRepository clientRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final SessionRepository sessionRepository;
    private final JwtService jwtService;

    public AuthService(ClientRepository clientRepository,
                       ClientAccountRepository clientAccountRepository,
                       SessionRepository sessionRepository,
                       JwtService jwtService) {
        this.clientRepository = clientRepository;
        this.clientAccountRepository = clientAccountRepository;
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public ClientRegistrationResponse register(RegisterClientRequest request) {
        if (clientRepository.findByEmail(request.email()) != null) {
            throw new ConflictException("CLIENT_ALREADY_EXISTS", "A client with that email already exists");
        }

        Client client = new Client(generatePositiveId(), request.name(), request.email());
        clientRepository.insertProfile(client, resolveDateOfBirth(request), resolveRiskProfile(request));

        ClientAccountRecord account = new ClientAccountRecord();
        account.setClientId(client.getClientId());
        account.setAccountType("DIRECT_TRADING");
        account.setCashBalance(resolveInitialCash(request));
        account.setCurrency("USD");
        account.setOpenedDate(LocalDate.now());
        clientAccountRepository.insert(account);

        return ClientRegistrationResponse.from(client.register(Instant.now()));
    }

    public SessionResponse signIn(SignInRequest request) {
        Client client = clientRepository.findByEmail(request.email());
        if (client == null) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "No fixture client exists for that email");
        }

        Client.SignIn signIn = client.signIn(request.email(), Instant.now());
        sessionRepository.insert(signIn.getSession().getSessionId(), signIn.getClientId(), signIn.getSession().getExpiresAt());
        String token = jwtService.generateToken(client, signIn.getSession());
        return SessionResponse.from(signIn, token);
    }

    public void signOut(AuthenticatedClient authenticatedClient) {
        Session session = sessionRepository.findBySessionId(authenticatedClient.sessionId());
        if (session == null) {
            return;
        }
        sessionRepository.updateExpiresAt(authenticatedClient.sessionId(), Instant.EPOCH);
    }

    private long generatePositiveId() {
        long value = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        return value > 0 ? value : 1L;
    }

    private LocalDate resolveDateOfBirth(RegisterClientRequest request) {
        return request.dateOfBirth() != null ? request.dateOfBirth() : LocalDate.of(1990, 1, 1);
    }

    private String resolveRiskProfile(RegisterClientRequest request) {
        if (request.riskProfile() == null || request.riskProfile().isBlank()) {
            return "Balanced";
        }
        return request.riskProfile().trim();
    }

    private BigDecimal resolveInitialCash(RegisterClientRequest request) {
        if (request.initialCash() == null) {
            return new BigDecimal("10000.00");
        }
        return request.initialCash();
    }
}
