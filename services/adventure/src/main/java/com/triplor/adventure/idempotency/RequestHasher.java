package com.triplor.adventure.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fingerprints a request, so that a replayed idempotency key can be told apart from a key
 * reused for a different request.
 *
 * <p>The hash is SHA-256 over the operation name and the request written as JSON in a canonical
 * form: object keys sorted, no extra whitespace. Two requests that mean the same thing must
 * hash the same, so callers pass the request after normalisation (trimmed text, sorted lists),
 * not the raw body.
 */
public final class RequestHasher {

    /** Its own mapper, so a change to the application's JSON settings cannot change stored hashes. */
    private static final JsonMapper CANONICAL = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            // Otherwise a record's components come first, in declaration order, unsorted.
            .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private RequestHasher() {
    }

    public static byte[] hash(String operation, Object canonicalRequest) {
        String material = operation + "\n" + canonicalJson(canonicalRequest);
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    static String canonicalJson(Object request) {
        return CANONICAL.writeValueAsString(request);
    }
}
