package com.rockettrading.rocket_trading.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MigrationIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Test void existingSchemaFailsSafelyUntilExplicitlyBaselinedAndDataSurvives() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(MigrationVersion.fromVersion("1")).load().migrate();
        var db = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        db.update("INSERT INTO client_profiles(client_id,client_full_name,email_address,date_of_birth,risk_profile) VALUES (1,'Existing','existing@example.com','1990-01-01','Balanced')");
        db.execute("DROP TABLE flyway_schema_history"); // Simulate the pre-Flyway schema in this disposable database.
        var migration = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
        assertThrows(RuntimeException.class, migration::migrate);
        assertEquals(1, db.queryForObject("SELECT count(*) FROM client_profiles", Integer.class));
        migration.baseline();
        migration.migrate();
        migration.migrate();
        assertEquals("existing@example.com", db.queryForObject("SELECT email_address FROM client_profiles WHERE client_id=1", String.class));
        assertEquals(5, db.queryForObject("SELECT count(*) FROM financial_instruments", Integer.class));
    }
}
