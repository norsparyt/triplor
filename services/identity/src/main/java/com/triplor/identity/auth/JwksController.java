package com.triplor.identity.auth;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes identity's public signing key as a JSON Web Key Set. Other services fetch it once,
 * cache it, and verify tokens locally from then on.
 */
@RestController
public class JwksController {

    private final JWKSet jwkSet;

    public JwksController(JWKSet jwkSet) {
        this.jwkSet = jwkSet;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        // toPublicJWKSet() strips the private key material. Never return jwkSet.toJSONObject(false).
        return jwkSet.toPublicJWKSet().toJSONObject();
    }
}
