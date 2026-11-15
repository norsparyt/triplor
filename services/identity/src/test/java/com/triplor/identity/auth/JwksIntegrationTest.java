package com.triplor.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import com.triplor.identity.IdentityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/** The contract other services rely on: a token can be verified with nothing but the JWKS URL. */
class JwksIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Value("${local.server.port}")
    int port;

    @Test
    void aTokenIsVerifiableUsingOnlyTheJwksUrl() throws Exception {
        String body = registerAnyone();
        String accessToken = JsonPath.read(body, "$.accessToken");
        String userId = JsonPath.read(body, "$.user.id");

        // This is exactly what another service does: fetch the keys over HTTP, verify locally.
        JwtDecoder remoteVerifier =
                NimbusJwtDecoder.withJwkSetUri("http://localhost:" + port + "/.well-known/jwks.json").build();
        Jwt jwt = remoteVerifier.decode(accessToken);

        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://identity.triplor.test");
    }

    @Test
    void theTokensKeyIdIsPublishedInTheJwksDocument() throws Exception {
        String accessToken = JsonPath.read(registerAnyone(), "$.accessToken");
        String keyId = SignedJWT.parse(accessToken).getHeader().getKeyID();

        String jwks = mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> publishedKeyIds = JsonPath.read(jwks, "$.keys[*].kid");

        assertThat(keyId).isNotBlank();
        assertThat(publishedKeyIds).contains(keyId);
    }

    @Test
    void theJwksDocumentNeverContainsPrivateKeyMaterial() throws Exception {
        String jwks = mvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> keys = JsonPath.read(jwks, "$.keys");

        assertThat(keys).isNotEmpty();
        for (Map<String, Object> key : keys) {
            // n and e are the public modulus and exponent. d, p, q, dp, dq and qi are private.
            assertThat(key).containsKeys("kty", "kid", "n", "e");
            assertThat(key).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
        }
    }

    private String registerAnyone() throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "user-%s@example.com", "password": "correct horse battery", "displayName": "Asha"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}
