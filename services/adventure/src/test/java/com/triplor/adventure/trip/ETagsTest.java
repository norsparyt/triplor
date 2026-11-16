package com.triplor.adventure.trip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ETagsTest {

    @Test
    void aVersionBecomesAQuotedString() {
        assertThat(ETags.of(0)).isEqualTo("\"0\"");
        assertThat(ETags.of(42)).isEqualTo("\"42\"");
    }

    @Test
    void anIssuedETagIsReadBack() {
        assertThat(ETags.parse(ETags.of(42))).hasValue(42);
        assertThat(ETags.parse("  \"7\" ")).hasValue(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "7", "\"\"", "\"abc\"", "\"-1\"", "W/\"7\"", "*", "\"7\", \"8\"", "\"99999999999999999999\""})
    void anythingElseMatchesNoVersion(String header) {
        assertThat(ETags.parse(header)).isEmpty();
    }
}
