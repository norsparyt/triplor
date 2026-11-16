package com.triplor.adventure.discovery;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.triplor.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The position of the last row of a search page: its start date and id, which is exactly what
 * the results are ordered by. The next page asks for rows after this position.
 *
 * <p>This is keyset pagination. Unlike {@code OFFSET n}, the database does not read and throw
 * away the first n rows, and rows inserted or removed while the user is paging cannot shift
 * the pages and cause a row to be shown twice or skipped.
 *
 * <p>Clients receive the cursor as an opaque string (base64url) and send it back unchanged.
 */
public record Cursor(LocalDate startDate, UUID id) {

    public String encode() {
        String plain = startDate + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        try {
            String plain = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = plain.indexOf('|');
            if (separator < 0) {
                throw invalid();
            }
            return new Cursor(
                    LocalDate.parse(plain.substring(0, separator)),
                    UUID.fromString(plain.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // Not base64, not a date, or not a UUID.
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "cursor is not valid");
    }
}
