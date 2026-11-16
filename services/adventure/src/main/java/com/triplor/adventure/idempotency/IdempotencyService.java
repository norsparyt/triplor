package com.triplor.adventure.idempotency;

import java.security.MessageDigest;
import java.util.UUID;
import java.util.function.Supplier;

import com.triplor.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes a create safe to retry. The client sends an {@code Idempotency-Key} header; however
 * many times the same request arrives with that key, the work is done once and every attempt
 * gets the same response.
 *
 * <p>The key row is written in the caller's transaction, together with the business change:
 *
 * <ol>
 *   <li>Insert the key with {@code ON CONFLICT DO NOTHING}.
 *   <li>A row was inserted: this is the first attempt. Run the action, then store its response
 *       on the key row. Both commit together, or neither does.
 *   <li>No row was inserted: the key is already there. Return the stored response, or refuse if
 *       the request differs from the one the key was first used for.
 * </ol>
 *
 * <p>Two requests with the same key at the same moment need no extra locking. The second
 * INSERT blocks on the primary key until the first transaction ends. If the first committed,
 * the second inserts nothing and replays the stored response. If the first rolled back, its
 * row is gone, and the second becomes the first attempt.
 *
 * <p>A failed action rolls back the key too, so nothing is stored and a retry starts afresh.
 */
@Service
public class IdempotencyService {

    public static final int MIN_KEY_LENGTH = 8;
    public static final int MAX_KEY_LENGTH = 128;

    /** The response, and whether it was replayed from an earlier attempt. */
    public record Result<T>(T response, boolean replayed) {
    }

    private record StoredAttempt(byte[] requestHash, Integer responseStatus, String responseBody) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public IdempotencyService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Runs {@code action} once per (user, key). Must be called inside the transaction that
     * {@code action} writes in, which {@code MANDATORY} enforces.
     *
     * @param canonicalRequest the request after normalisation; see {@link RequestHasher}
     * @param responseStatus the HTTP status the first attempt answers with, kept for the record
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Result<T> execute(UUID userId, String key, String operation, Object canonicalRequest,
            int responseStatus, Class<T> responseType, Supplier<T> action) {
        checkKey(key);
        byte[] requestHash = RequestHasher.hash(operation, canonicalRequest);

        int inserted = jdbc.sql("""
                        INSERT INTO idempotency_keys (user_id, idem_key, operation, request_hash)
                        VALUES (:userId, :key, :operation, :requestHash)
                        ON CONFLICT (user_id, idem_key) DO NOTHING
                        """)
                .param("userId", userId)
                .param("key", key)
                .param("operation", operation)
                .param("requestHash", requestHash)
                .update();

        if (inserted == 1) {
            T response = action.get();
            jdbc.sql("""
                            UPDATE idempotency_keys
                               SET response_status = :status, response_body = CAST(:body AS jsonb)
                             WHERE user_id = :userId AND idem_key = :key
                            """)
                    .param("status", responseStatus)
                    .param("body", json.writeValueAsString(response))
                    .param("userId", userId)
                    .param("key", key)
                    .update();
            return new Result<>(response, false);
        }

        StoredAttempt stored = jdbc.sql("""
                        SELECT request_hash, response_status, CAST(response_body AS text) AS response_body
                          FROM idempotency_keys
                         WHERE user_id = :userId AND idem_key = :key
                        """)
                .param("userId", userId)
                .param("key", key)
                .query((rs, rowNum) -> new StoredAttempt(
                        rs.getBytes("request_hash"),
                        (Integer) rs.getObject("response_status"),
                        rs.getString("response_body")))
                .single();

        if (!MessageDigest.isEqual(stored.requestHash(), requestHash)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request");
        }
        if (stored.responseBody() == null) {
            // Cannot happen: a key row only becomes visible to others when its transaction
            // commits, and by then the response is stored on it.
            throw new IllegalStateException("Idempotency key without a stored response");
        }
        return new Result<>(json.readValue(stored.responseBody(), responseType), true);
    }

    private static void checkKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "The Idempotency-Key header is required");
        }
        boolean printableAscii = key.chars().allMatch(c -> c > 0x20 && c < 0x7F);
        if (key.length() < MIN_KEY_LENGTH || key.length() > MAX_KEY_LENGTH || !printableAscii) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Idempotency-Key must be " + MIN_KEY_LENGTH + " to " + MAX_KEY_LENGTH
                            + " printable ASCII characters without spaces");
        }
    }
}
