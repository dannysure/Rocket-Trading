package com.rockettrading.rocket_trading.config;

import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.service.AccountProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The representative sign-in fixture (BRS section 10): one client and one identity per internal role.
 * Created only when FIXTURE_PASSWORD is set, so production deployments never get shared accounts.
 */
@Component
public class FixtureLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FixtureLoader.class);
    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("Joanna Hart", "joanna@example.com", "CLIENT"),
            new Fixture("Priya Shah", "priya@rocket.internal", "ANALYST"),
            new Fixture("David Okafor", "david@rocket.internal", "OPERATIONS")
    );

    private final ClientRepository clientRepository;
    private final AccountProvisioningService accountProvisioningService;
    private final PasswordEncoder passwordEncoder;
    private final String fixturePassword;

    public FixtureLoader(ClientRepository clientRepository,
                         AccountProvisioningService accountProvisioningService,
                         PasswordEncoder passwordEncoder,
                         @Value("${fixtures.password:}") String fixturePassword) {
        this.clientRepository = clientRepository;
        this.accountProvisioningService = accountProvisioningService;
        this.passwordEncoder = passwordEncoder;
        this.fixturePassword = fixturePassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (fixturePassword == null || fixturePassword.isBlank()) {
            return;
        }
        for (Fixture fixture : FIXTURES) {
            Client client = clientRepository.findByEmail(fixture.email());
            if (client == null) {
                client = new Client(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), fixture.name(), fixture.email());
                clientRepository.insertProfile(client, null, "Balanced");
                log.info("Created {} fixture {}", fixture.role(), fixture.email());
            }
            // Existing passwords are left alone so a fixture user's own change survives restarts
            if (clientRepository.findPasswordHash(client.getClientId()) == null) {
                clientRepository.updatePasswordHash(client.getClientId(), passwordEncoder.encode(fixturePassword));
            }
            clientRepository.updateRole(client.getClientId(), fixture.role());
            if ("CLIENT".equals(fixture.role())) {
                accountProvisioningService.ensureDirectTradingAccount(client.getClientId(), null);
            }
        }
    }

    private record Fixture(String name, String email, String role) {
    }
}
