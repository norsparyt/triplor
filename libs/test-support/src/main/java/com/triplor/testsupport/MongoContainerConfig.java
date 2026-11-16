package com.triplor.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Import this in an integration test to run it against a real MongoDB in Docker, the same
 * major version Compose runs. {@code @ServiceConnection} points the application at it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MongoContainerConfig {

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer("mongo:8.0");
    }
}
