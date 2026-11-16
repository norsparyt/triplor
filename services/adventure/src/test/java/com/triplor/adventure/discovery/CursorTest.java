package com.triplor.adventure.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import com.triplor.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CursorTest {

    @Test
    void aCursorSurvivesEncodingAndDecoding() {
        Cursor cursor = new Cursor(LocalDate.parse("2030-03-01"), UUID.randomUUID());

        assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void theEncodedFormIsSafeToPutInAUrl() {
        String encoded = new Cursor(LocalDate.parse("2030-03-01"), UUID.randomUUID()).encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not base64 !!", "aGVsbG8", "MjAzMC0wMy0wMXxub3QtYS11dWlk", "bm90LWEtZGF0ZXwwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDA"})
    void anythingElseIsRejectedAsAValidationFailure(String garbage) {
        assertThatThrownBy(() -> Cursor.decode(garbage))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void theGarbageSamplesAreWhatTheyClaimToBe() {
        // "hello", a date with a bad id, and a bad date with a valid id.
        assertThat(decoded("aGVsbG8")).isEqualTo("hello");
        assertThat(decoded("MjAzMC0wMy0wMXxub3QtYS11dWlk")).isEqualTo("2030-03-01|not-a-uuid");
        assertThat(decoded("bm90LWEtZGF0ZXwwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDA"))
                .isEqualTo("not-a-date|00000000-0000-0000-0000-000000000000");
    }

    private static String decoded(String base64Url) {
        return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
    }
}
