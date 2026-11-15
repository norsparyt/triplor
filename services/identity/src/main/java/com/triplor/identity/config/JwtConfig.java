package com.triplor.identity.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Identity's signing key and everything derived from it.
 *
 * <p>Tokens are signed with RS256: identity holds the private key and signs; every other service
 * verifies with the public key, which identity publishes at {@code /.well-known/jwks.json}.
 * No other service can mint a token, and none needs to call identity to check one.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /**
     * The key pair as a JWK. Its key id is the thumbprint of the public key, so the id changes
     * whenever the key does. That lets the JWKS endpoint publish an old and a new key side by
     * side during a rotation, and lets verifiers pick the right one by the token's {@code kid}.
     */
    @Bean
    RSAKey signingKey(JwtProperties properties) {
        RSAPrivateCrtKey privateKey = readPrivateKey(Path.of(properties.privateKeyPath()));
        try {
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException("Could not build the JWT signing key", e);
        }
    }

    @Bean
    JWKSet jwkSet(RSAKey signingKey) {
        return new JWKSet(signingKey);
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSet jwkSet) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet));
    }

    /**
     * Identity verifies its own tokens with the public half of its key, and applies the same
     * checks the other services do: signature, expiry, issuer and audience.
     */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtAudienceValidator(properties.audience())));
        return decoder;
    }

    private static RSAPrivateCrtKey readPrivateKey(Path path) {
        String pem;
        try {
            pem = Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "JWT private key not found at " + path.toAbsolutePath() + ". Run `make keys` to create one.", e);
        }
        if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new IllegalStateException(
                    "The JWT private key at " + path.toAbsolutePath() + " must be an unencrypted PKCS#8 PEM file.");
        }
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("The JWT private key at " + path.toAbsolutePath() + " is not a valid RSA key.", e);
        }
    }
}
