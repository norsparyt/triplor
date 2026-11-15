package com.triplor.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Import this in a service's tests to make it trust tokens from {@link TestTokens} instead of
 * fetching identity's public key over HTTP.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtDecoderConfig {

    @Bean
    JwtDecoder testJwtDecoder() {
        return TestTokens.decoder();
    }
}
