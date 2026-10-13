package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.JwtProperties;
import com.rockettrading.rocket_trading.dto.auth.RegisterClientRequest;
import com.rockettrading.rocket_trading.dto.auth.SessionResponse;
import com.rockettrading.rocket_trading.dto.auth.SignInRequest;
import com.rockettrading.rocket_trading.dto.auth.UpdateProfileRequest;
import com.rockettrading.rocket_trading.exception.ApiException;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.exception.UnauthorizedException;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.model.Session;
import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.repository.SessionRepository;
import com.rockettrading.rocket_trading.repository.TransactionRepository;
import com.rockettrading.rocket_trading.security.AuthenticatedClient;
import com.rockettrading.rocket_trading.security.JwtService;
import com.rockettrading.rocket_trading.security.LoginAttemptGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceLayerTest {
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private ClientRepository clientRepository;
    private SessionRepository sessionRepository;
    private ClientAccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("rocket-trading");
        jwtProperties.setSecret("change-me-development-jwt-secret-1234567890");
        jwtProperties.setExpirationHours(8);
        jwtService = new JwtService(jwtProperties);
        clientRepository = mock(ClientRepository.class);
        sessionRepository = mock(SessionRepository.class);
        accountRepository = mock(ClientAccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        authService = new AuthService(clientRepository, sessionRepository, jwtService, passwordEncoder,
                new AccountProvisioningService(accountRepository, transactionRepository), new LoginAttemptGuard());
    }

    @Test
    void registerStoresOnlyAHashOfThePasswordAndOpensAnAccount() {
        when(clientRepository.findByEmail("joanna@example.com")).thenReturn(null);

        var response = authService.register(new RegisterClientRequest("Joanna", "joanna@example.com", "correct-horse",
                LocalDate.of(1992, 5, 12), "Balanced", new BigDecimal("25000.00")));

        assertEquals("joanna@example.com", response.email());
        verify(clientRepository).insertProfile(any(Client.class), any(LocalDate.class), eq("Balanced"));
        verify(clientRepository).updatePasswordHash(anyLong(), argThat(hash ->
                !hash.contains("correct-horse") && passwordEncoder.matches("correct-horse", hash)));
        verify(accountRepository).insert(argThat(account -> new BigDecimal("25000.00").equals(account.getCashBalance())
                && "DIRECT_TRADING".equals(account.getAccountType())));
        // The opening balance is a ledger deposit, so cash is always explained by the ledger
        verify(transactionRepository).insert(argThat(entry -> "DEPOSIT".equals(entry.getTransactionType())
                && new BigDecimal("25000.00").equals(entry.getNetAmount())));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(clientRepository.findByEmail("joanna@example.com")).thenReturn(new Client(1L, "Joanna", "joanna@example.com"));

        assertThrows(ConflictException.class, () -> authService.register(
                new RegisterClientRequest("Joanna", "joanna@example.com", "correct-horse", null, null, null)));
    }

    @Test
    void signInIssuesATokenCarryingTheRole() {
        Client analyst = new Client(7L, "Priya", "priya@rocket.internal");
        analyst.setRole("ANALYST");
        when(clientRepository.findByEmail("priya@rocket.internal")).thenReturn(analyst);
        when(clientRepository.findPasswordHash(7L)).thenReturn(passwordEncoder.encode("correct-horse"));

        SessionResponse response = authService.signIn(new SignInRequest("priya@rocket.internal", "correct-horse"));

        assertEquals(7L, response.clientId());
        assertEquals("ANALYST", response.role());
        assertEquals("ANALYST", jwtService.parseToken(response.accessToken()).role());
        verify(sessionRepository).insert(anyLong(), eq(7L), any(Instant.class));
    }

    @Test
    void unknownEmailWrongPasswordAndOAuthOnlyAccountsGetTheSameAnswer() {
        Client oauthOnly = new Client(8L, "Gina", "gina@example.com");
        when(clientRepository.findByEmail("gina@example.com")).thenReturn(oauthOnly);
        when(clientRepository.findPasswordHash(8L)).thenReturn(null);
        Client joanna = new Client(9L, "Joanna", "joanna@example.com");
        when(clientRepository.findByEmail("joanna@example.com")).thenReturn(joanna);
        when(clientRepository.findPasswordHash(9L)).thenReturn(passwordEncoder.encode("correct-horse"));

        for (SignInRequest attempt : new SignInRequest[]{
                new SignInRequest("missing@example.com", "anything-at-all"),
                new SignInRequest("gina@example.com", "anything-at-all"),
                new SignInRequest("joanna@example.com", "wrong-password")}) {
            UnauthorizedException failure = assertThrows(UnauthorizedException.class, () -> authService.signIn(attempt));
            assertEquals("Email or password is incorrect", failure.getMessage());
        }
        verify(sessionRepository, never()).insert(anyLong(), anyLong(), any());
    }

    @Test
    void repeatedFailuresLockTheEmailOutEvenForTheRightPassword() {
        Client joanna = new Client(9L, "Joanna", "joanna@example.com");
        when(clientRepository.findByEmail("joanna@example.com")).thenReturn(joanna);
        when(clientRepository.findPasswordHash(9L)).thenReturn(passwordEncoder.encode("correct-horse"));
        for (int i = 0; i < 5; i++) {
            assertThrows(UnauthorizedException.class, () -> authService.signIn(new SignInRequest("joanna@example.com", "wrong-password")));
        }

        ApiException locked = assertThrows(ApiException.class,
                () -> authService.signIn(new SignInRequest("JOANNA@example.com", "correct-horse")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.getStatus());
    }

    @Test
    void signOutRevokesExistingSession() {
        when(sessionRepository.findBySessionId(101L)).thenReturn(new Session(101L, Instant.now().plusSeconds(60)));

        assertDoesNotThrow(() -> authService.signOut(new AuthenticatedClient(7L, 101L, "joanna@example.com")));
        verify(sessionRepository).updateExpiresAt(101L, Instant.EPOCH);
    }

    @Test
    void oauthExchangeRevokesTheRedirectTokenAndIssuesAFreshSession() {
        when(clientRepository.findById(7L)).thenReturn(new Client(7L, "Joanna", "joanna@example.com"));

        var response = authService.getOAuthUserInfo(new AuthenticatedClient(7L, 555L, "joanna@example.com"));

        verify(sessionRepository).updateExpiresAt(555L, Instant.EPOCH);
        assertNotEquals(555L, jwtService.parseToken(response.accessToken()).sessionId());
        assertEquals("CLIENT", response.role());
        assertFalse(response.profileComplete());
    }

    @Test
    void oauthExchangeReportsTheProfileCompleteOnceADateOfBirthIsStored() {
        when(clientRepository.findById(7L)).thenReturn(new Client(7L, "Joanna", "joanna@example.com"));
        when(clientRepository.findDateOfBirth(7L)).thenReturn(LocalDate.of(1992, 5, 12));

        var response = authService.getOAuthUserInfo(new AuthenticatedClient(7L, 555L, "joanna@example.com"));

        assertTrue(response.profileComplete());
    }

    @Test
    void updateProfileStoresAValidAdultDateOfBirth() {
        when(clientRepository.findById(7L)).thenReturn(new Client(7L, "Joanna", "joanna@example.com"));

        authService.updateProfile(7L, new UpdateProfileRequest(" Joanna Lee ", LocalDate.of(1992, 5, 12), "Cautious"));

        verify(clientRepository).updateProfile(7L, "Joanna Lee", LocalDate.of(1992, 5, 12), "Cautious");
    }

    @Test
    void updateProfileRejectsMinorsAndStoresNothing() {
        when(clientRepository.findById(7L)).thenReturn(new Client(7L, "Joanna", "joanna@example.com"));
        LocalDate seventeen = LocalDate.now().minusYears(18).plusDays(1);

        ApiException failure = assertThrows(ApiException.class,
                () -> authService.updateProfile(7L, new UpdateProfileRequest("Joanna", seventeen, "Balanced")));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals("INVALID_DATE_OF_BIRTH", failure.getCode());
        verify(clientRepository, never()).updateProfile(anyLong(), any(), any(), any());
    }

    @Test
    void dateOfBirthMustBeRealAndAtLeastEighteenYearsAgo() {
        LocalDate today = LocalDate.of(2026, 10, 13);

        assertDoesNotThrow(() -> AuthService.checkDateOfBirth(LocalDate.of(2008, 10, 13), today));
        assertDoesNotThrow(() -> AuthService.checkDateOfBirth(LocalDate.of(1950, 1, 1), today));
        // Born on 29 February: turns 18 on 1 March in a non-leap year
        assertThrows(ApiException.class, () -> AuthService.checkDateOfBirth(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 28)));
        assertDoesNotThrow(() -> AuthService.checkDateOfBirth(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 3, 1)));

        for (LocalDate invalid : new LocalDate[]{
                LocalDate.of(2008, 10, 14),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(1800, 1, 1),
                null}) {
            ApiException failure = assertThrows(ApiException.class, () -> AuthService.checkDateOfBirth(invalid, today));
            assertEquals("INVALID_DATE_OF_BIRTH", failure.getCode());
        }
    }

    @Test
    void registerRejectsAnUnderageDateOfBirth() {
        when(clientRepository.findByEmail("teen@example.com")).thenReturn(null);

        assertThrows(ApiException.class, () -> authService.register(new RegisterClientRequest("Teen", "teen@example.com",
                "correct-horse", LocalDate.now().minusYears(15), null, null)));
        verify(clientRepository, never()).insertProfile(any(), any(), any());
    }
}
