package com.triplor.adventure.trip;

import java.util.OptionalLong;

/**
 * Converts between an adventure's version number and the HTTP ETag that carries it.
 * Version 3 is sent as the header value {@code "3"}, quotes included.
 */
public final class ETags {

    private ETags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Reads the version out of an {@code If-Match} header. Empty if the value is not an ETag
     * this service could have issued; such a value cannot match any version.
     */
    public static OptionalLong parse(String headerValue) {
        String value = headerValue.trim();
        if (value.length() < 3 || !value.startsWith("\"") || !value.endsWith("\"")) {
            return OptionalLong.empty();
        }
        String digits = value.substring(1, value.length() - 1);
        if (digits.length() > 18 || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(digits));
    }
}
