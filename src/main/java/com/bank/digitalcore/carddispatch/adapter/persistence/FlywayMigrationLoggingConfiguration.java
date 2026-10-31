package com.bank.digitalcore.carddispatch.adapter.persistence;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Logs the start and successful completion of database migrations. */
@Configuration
public class FlywayMigrationLoggingConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationLoggingConfiguration.class);

    @Bean
    FlywayMigrationStrategy cardDispatchFlywayMigrationStrategy() {
        return (Flyway flyway) -> {
            log.info("Starting Flyway migrations");
            flyway.migrate();
            log.info("Flyway migrations completed successfully");
        };
    }
}
