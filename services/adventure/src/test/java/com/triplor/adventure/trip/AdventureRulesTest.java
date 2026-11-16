package com.triplor.adventure.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.triplor.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdventureRulesTest {

    static final LocalDate TODAY = LocalDate.parse("2030-06-15");

    @Test
    void aTripMayStartAndEndOnTheSameDay() {
        assertThatCode(() -> AdventureRules.checkDates(TODAY, TODAY, TODAY, true)).doesNotThrowAnyException();
    }

    @Test
    void theEndCannotComeBeforeTheStart() {
        assertInvalid(() -> AdventureRules.checkDates(TODAY.plusDays(5), TODAY.plusDays(4), TODAY, true),
                "endDate must not be before startDate");
    }

    @Test
    void aTripCanBeAtMostNinetyDaysCountingBothEnds() {
        LocalDate start = TODAY.plusDays(10);

        assertThatCode(() -> AdventureRules.checkDates(start, start.plusDays(89), TODAY, true))
                .doesNotThrowAnyException();
        assertInvalid(() -> AdventureRules.checkDates(start, start.plusDays(90), TODAY, true), "at most 90 days");
    }

    @Test
    void aNewStartDateCannotBeInThePast() {
        // Yesterday in UTC is still "today" for a traveller west of UTC, so it is accepted.
        assertThatCode(() -> AdventureRules.checkDates(TODAY.minusDays(1), TODAY.plusDays(2), TODAY, true))
                .doesNotThrowAnyException();
        assertInvalid(() -> AdventureRules.checkDates(TODAY.minusDays(2), TODAY.plusDays(2), TODAY, true),
                "must not be in the past");
    }

    @Test
    void aNewStartDateCannotBeMoreThanAYearAhead() {
        assertThatCode(() -> AdventureRules.checkDates(TODAY.plusDays(365), TODAY.plusDays(366), TODAY, true))
                .doesNotThrowAnyException();
        assertInvalid(() -> AdventureRules.checkDates(TODAY.plusDays(366), TODAY.plusDays(367), TODAY, true),
                "within 365 days");
    }

    @Test
    void anUnchangedStartDateMayAlreadyHavePassed() {
        // Editing the end date of a trip that began last week.
        assertThatCode(() -> AdventureRules.checkDates(TODAY.minusDays(7), TODAY.plusDays(3), TODAY, false))
                .doesNotThrowAnyException();
        // The other rules still apply.
        assertInvalid(() -> AdventureRules.checkDates(TODAY.minusDays(7), TODAY.minusDays(8), TODAY, false),
                "endDate must not be before startDate");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 20})
    void capacityFromOneToTwentyIsAccepted(int capacity) {
        assertThatCode(() -> AdventureRules.checkCapacity(capacity)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 21})
    void capacityOutsideOneToTwentyIsRejected(int capacity) {
        assertInvalid(() -> AdventureRules.checkCapacity(capacity), "between 1 and 20");
    }

    @Test
    void stylesAreStoredSortedAndWithoutDuplicates() {
        assertThat(AdventureRules.normalizeStyles(
                List.of(TravelStyle.HIKING, TravelStyle.CULTURE, TravelStyle.HIKING)))
                .containsExactly("CULTURE", "HIKING");
        assertInvalid(() -> AdventureRules.normalizeStyles(List.of()), "at least one style");
    }

    @Test
    void theDescriptionIsTrimmedAndLimited() {
        assertThat(AdventureRules.normalizeDescription("  Forts and food \n")).isEqualTo("Forts and food");
        assertThat(AdventureRules.normalizeDescription("x".repeat(300))).hasSize(300);
        assertInvalid(() -> AdventureRules.normalizeDescription("   "), "must not be blank");
        assertInvalid(() -> AdventureRules.normalizeDescription("x".repeat(301)), "at most 300");
    }

    private static void assertInvalid(Runnable action, String messagePart) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"))
                .hasMessageContaining(messagePart);
    }
}
