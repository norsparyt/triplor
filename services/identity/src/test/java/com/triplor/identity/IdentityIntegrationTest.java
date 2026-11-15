package com.triplor.identity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.triplor.testsupport.PostgresContainerConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for identity's integration tests: the whole application, a real PostgreSQL in
 * Docker (schema created by Flyway), and a signing key generated for the test run.
 *
 * <p>Every subclass shares one application context and therefore one database, so tests must not
 * assume empty tables. Use unique emails.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
public abstract class IdentityIntegrationTest {

    private static final Path PRIVATE_KEY_FILE = writeTemporaryPrivateKey();

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("triplor.jwt.private-key-path", PRIVATE_KEY_FILE::toString);
        registry.add("triplor.jwt.issuer", () -> "https://identity.triplor.test");
    }

    /** Writes a fresh RSA key in the same format `make keys` produces (unencrypted PKCS#8 PEM). */
    private static Path writeTemporaryPrivateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            byte[] pkcs8 = generator.generateKeyPair().getPrivate().getEncoded();
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs8)
                    + "\n-----END PRIVATE KEY-----\n";
            Path file = Files.createTempFile("triplor-test-jwt", ".pem");
            file.toFile().deleteOnExit();
            return Files.writeString(file, pem);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
