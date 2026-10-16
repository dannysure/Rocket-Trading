package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.auth.ClientRegistrationResponse;
import com.rockettrading.rocket_trading.dto.auth.OAuthLoginResponse;
import com.rockettrading.rocket_trading.dto.auth.RegisterClientRequest;
import com.rockettrading.rocket_trading.dto.auth.SessionResponse;
import com.rockettrading.rocket_trading.dto.auth.SignInRequest;
import com.rockettrading.rocket_trading.dto.auth.UpdateProfileRequest;
import com.rockettrading.rocket_trading.exception.ApiException;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.exception.UnauthorizedException;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.repository.SessionRepository;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.security.JwtService;
import com.rockettrading.rocket_trading.security.LoginAttemptGuard;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {
    static final Duration SESSION_LENGTH = Duration.ofHours(8);
    // Same answer for an unknown email and a wrong password, so sign-in never reveals who is registered
    private static final String INVALID_CREDENTIALS = "Email or password is incorrect";
    static final int MINIMUM_AGE = 18;
    // Anything older is a typo (e.g. 1092 for 1992), not a real client
    static final int MAXIMUM_AGE = 120;

    private final ClientRepository clientRepository;
    private final SessionRepository sessionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AccountProvisioningService accountProvisioningService;
    private final LoginAttemptGuard loginAttemptGuard;

    public AuthService(ClientRepository clientRepository,
                       SessionRepository sessionRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AccountProvisioningService accountProvisioningService,
                       LoginAttemptGuard loginAttemptGuard) {
        this.clientRepository = clientRepository;
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.accountProvisioningService = accountProvisioningService;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    @Transactional
    public ClientRegistrationResponse register(RegisterClientRequest request) {
        if (clientRepository.findByEmail(request.email()) != null) {
            throw new ConflictException("CLIENT_ALREADY_EXISTS", "A client with that email already exists");
        }

        if (request.dateOfBirth() != null) {
            checkDateOfBirth(request.dateOfBirth(), LocalDate.now());
        }

        Client client = new Client(generatePositiveId(), request.name(), request.email());
        clientRepository.insertProfile(client, request.dateOfBirth(), resolveRiskProfile(request));
        clientRepository.updatePasswordHash(client.getClientId(), passwordEncoder.encode(request.password()));
        accountProvisioningService.ensureDirectTradingAccount(client.getClientId(), request.initialCash());

        return ClientRegistrationResponse.from(client.register(Instant.now()));
    }

    public SessionResponse signIn(SignInRequest request) {
        loginAttemptGuard.checkAllowed(request.email());
        Client client = clientRepository.findByEmail(request.email());
        String hash = client == null ? null : clientRepository.findPasswordHash(client.getClientId());
        // OAuth-only accounts have no password and must use their provider
        if (hash == null || !passwordEncoder.matches(request.password(), hash)) {
            loginAttemptGuard.recordFailure(request.email());
            throw new UnauthorizedException("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }
        loginAttemptGuard.recordSuccess(request.email());
        return startSession(client);
    }

    public void signOut(AuthenticatedClient authenticatedClient) {
        Session session = sessionRepository.findBySessionId(authenticatedClient.sessionId());
        if (session == null) {
            return;
        }
        sessionRepository.updateExpiresAt(authenticatedClient.sessionId(), Instant.EPOCH);
    }

    /** Revokes all of the client's sessions, on every device, including the one making the request. */
    public void signOutEverywhere(AuthenticatedClient authenticatedClient) {
        sessionRepository.expireAllForClient(authenticatedClient.clientId(), Instant.EPOCH);
    }

    @Transactional
    public ClientRegistrationResponse updateProfile(long clientId, UpdateProfileRequest request) {
        Client client = clientRepository.findById(clientId);
        if (client == null) {
            throw new UnauthorizedException("CLIENT_NOT_FOUND", "Client not found");
        }

        checkDateOfBirth(request.dateOfBirth(), LocalDate.now());
        client.setName(request.name().trim());
        String riskProfile = request.riskProfile() != null ? request.riskProfile() : "Balanced";
        clientRepository.updateProfile(clientId, client.getName(), request.dateOfBirth(), riskProfile);

        return ClientRegistrationResponse.from(client.register(Instant.now()));
    }

    /**
     * Exchanges the one-time token from the GitHub redirect for the session the browser keeps,
     * and revokes the redirect token's session so it cannot be replayed from browser history.
     */
    public OAuthLoginResponse getOAuthUserInfo(AuthenticatedClient redirectSession) {
        Client client = clientRepository.findById(redirectSession.clientId());
        if (client == null) {
            throw new UnauthorizedException("CLIENT_NOT_FOUND", "Client not found");
        }
        sessionRepository.updateExpiresAt(redirectSession.sessionId(), Instant.EPOCH);
        SessionResponse session = startSession(client);
        return OAuthLoginResponse.success(
                client.getClientId(),
                client.getEmail(),
                client.getName(),
                session.accessToken(),
                SESSION_LENGTH.toSeconds(),
                client.getRole(),
                clientRepository.findDateOfBirth(client.getClientId()) != null
        );
    }

    /** Rejects a date of birth in the future, implausibly long ago, or belonging to someone under 18. */
    static void checkDateOfBirth(LocalDate dateOfBirth, LocalDate today) {
        if (dateOfBirth == null) {
            throw invalidDateOfBirth("Enter your date of birth");
        }
        if (dateOfBirth.isAfter(today)) {
            throw invalidDateOfBirth("Date of birth cannot be in the future");
        }
        int age = Period.between(dateOfBirth, today).getYears();
        if (age > MAXIMUM_AGE) {
            throw invalidDateOfBirth("Enter a valid date of birth");
        }
        if (age < MINIMUM_AGE) {
            throw invalidDateOfBirth("You must be at least " + MINIMUM_AGE + " to open an account");
        }
    }

    private static ApiException invalidDateOfBirth(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_OF_BIRTH", message);
    }

    private SessionResponse startSession(Client client) {
        Session session = new Session(generatePositiveId(), Instant.now().plus(SESSION_LENGTH));
        sessionRepository.insert(session.getSessionId(), client.getClientId(), session.getExpiresAt());
        return SessionResponse.from(client, session, jwtService.generateToken(client, session));
    }

    private long generatePositiveId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private String resolveRiskProfile(RegisterClientRequest request) {
        if (request.riskProfile() == null || request.riskProfile().isBlank()) {
            return "Balanced";
        }
        return request.riskProfile().trim();
    }
}
