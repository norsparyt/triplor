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
    public static final long PARIS_TEXAS = 4717560;
    public static final long MUMBAI = 1275339;
    public static final long PUNE = 1259229;
    public static final long BENGALURU = 1277333;
    public static final long CHENNAI = 1264527;
    public static final long LEH = 1264733;
    public static final long KATHMANDU = 1283240;
    public static final long BANGKOK = 1609350;
    public static final long ZURICH = 2657896;
    public static final long LONDON_UK = 2643743;
    public static final long LONDON_CANADA = 6058560;
    public static final long NEW_YORK = 5128581;
    public static final long TOKYO = 1850147;
}
