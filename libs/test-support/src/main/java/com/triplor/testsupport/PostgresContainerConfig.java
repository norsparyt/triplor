package com.triplor.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Import this in an integration test to run it against a real PostgreSQL in Docker.
 *
 * <p>The container uses the project's own image (PostgreSQL 17 with PostGIS), the same one
 * Compose runs, so tests see the same database as local development. Build it first with
 * {@code make db-image}. {@code @ServiceConnection} points the application's datasource at the
 * container, and Spring's test context cache keeps one container for the whole test run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    public static final DockerImageName IMAGE =
            DockerImageName.parse("triplor/postgres:17").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE);
    }
}
