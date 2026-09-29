package io.github.nicolassanchez1.technicaltestdavivienda.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real backing services for integration tests. Full-text search behaviour cannot be
 * mocked, so every integration test runs against the same PostgreSQL image as production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfiguration {

    static final String POSTGRES_IMAGE = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
