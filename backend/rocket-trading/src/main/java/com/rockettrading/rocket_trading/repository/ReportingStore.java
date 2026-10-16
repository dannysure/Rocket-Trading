package com.rockettrading.rocket_trading.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * The reporting database (BR-16): its own connection pool, its own schema and, when
 * reporting.datasource.url is set, its own PostgreSQL server. Analyst queries run only here, so they
 * can never hold a trading connection or lock a trading table.
 *
 * Deliberately not a DataSource or TransactionManager bean, so Spring Boot keeps auto-configuring
 * the trading database, Flyway and MyBatis exactly as before.
 */
@Component
public class ReportingStore {
    private static final Logger log = LoggerFactory.getLogger(ReportingStore.class);

    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final boolean separateDatabase;

    public ReportingStore(@Value("${reporting.datasource.url:}") String url,
                          @Value("${reporting.datasource.username:}") String username,
                          @Value("${reporting.datasource.password:}") String password,
                          @Value("${reporting.datasource.max-pool-size:4}") int maxPoolSize,
                          JdbcConnectionDetails trading) {
        separateDatabase = StringUtils.hasText(url);
        if (!separateDatabase) {
            // Local development without a reporting server: a separate schema and pool on the trading database
            log.warn("reporting.datasource.url is not set; reporting uses its own schema and pool on the trading database");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("reporting");
        config.setJdbcUrl(separateDatabase ? url : trading.getJdbcUrl());
        config.setUsername(separateDatabase && StringUtils.hasText(username) ? username : trading.getUsername());
        config.setPassword(separateDatabase && StringUtils.hasText(password) ? password : trading.getPassword());
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(10_000);
        config.setSchema("reporting");
        dataSource = new HikariDataSource(config);

        Flyway.configure()
                .dataSource(dataSource)
                .schemas("reporting")
                .table("flyway_reporting_history")
                .locations("classpath:db/reporting")
                .cleanDisabled(true)
                .load()
                .migrate();

        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    public TransactionTemplate transactions() {
        return transactions;
    }

    /** True when reporting runs on its own database server rather than a schema of the trading database. */
    public boolean isSeparateDatabase() {
        return separateDatabase;
    }

    @PreDestroy
    void close() {
        dataSource.close();
    }
}
