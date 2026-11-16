package com.triplor.adventure;

import com.triplor.testsupport.PostgresContainerConfig;
import com.triplor.testsupport.TestJwtDecoderConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Base class for the adventure service's integration tests: the whole application against a
 * real PostgreSQL with PostGIS in Docker. Flyway creates the schema and, from a test-only
 * location, loads a fixture of twenty places. Callers are authenticated with tokens from
 * {@code TestTokens}, so the identity service is not needed.
 *
 * <p>Every subclass shares one application context and one database, so tests must not assume
 * empty tables. Use a fresh user id per test.
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/testdata")
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, TestJwtDecoderConfig.class})
public abstract class AdventureIntegrationTest {

    public static final long JAIPUR = 1269515;
    public static final long MANALI = 1263968;
    public static final long PARIS_FRANCE = 2988507;
}
