package com.triplor.adventure.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.triplor.common.error.ApiException;
import org.junit.jupiter.api.Test;

class SearchCriteriaTest {

    static final LocalDate TODAY = LocalDate.parse("2030-06-15");

    @Test
    void onlyThePointIsRequiredAndTheRestHasDefaults() {
        SearchCriteria criteria = SearchCriteria.of(26.9, 75.8, null, null, null, null, null, null, TODAY);

        assertThat(criteria.radiusKm()).isEqualTo(50);
        assertThat(criteria.radiusMeters()).isEqualTo(50_000);
        assertThat(criteria.from()).isEqualTo(TODAY);
        assertThat(criteria.to()).isEqualTo(TODAY.plusDays(90));
        assertThat(criteria.styles()).isEmpty();
        assertThat(criteria.hasStyles()).isFalse();
        assertThat(criteria.cursor()).isNull();
        assertThat(criteria.limit()).isEqualTo(20);
    }

    @Test
    void theDefaultWindowFollowsAnExplicitFrom() {
        SearchCriteria criteria =
                SearchCriteria.of(26.9, 75.8, null, TODAY.plusDays(200), null, null, null, null, TODAY);

        assertThat(criteria.to()).isEqualTo(TODAY.plusDays(290));
    }

    @Test
    void thePointMustBeOnEarth() {
        assertInvalid(() -> of(null, 75.8), "lat and lng are required");
        assertInvalid(() -> of(26.9, null), "lat and lng are required");
        assertInvalid(() -> of(90.1, 75.8), "lat must be between");
        assertInvalid(() -> of(26.9, -180.1), "lng must be between");
        assertInvalid(() -> of(Double.NaN, 75.8), "lat must be between");
        assertThat(of(-90.0, 180.0).lat()).isEqualTo(-90);
    }

    @Test
    void theRadiusIsPositiveAndAtMostFiveHundredKilometres() {
        assertThat(SearchCriteria.of(26.9, 75.8, 500.0, null, null, null, null, null, TODAY).radiusKm()).isEqualTo(500);
        assertInvalid(() -> SearchCriteria.of(26.9, 75.8, 500.1, null, null, null, null, null, TODAY), "radiusKm");
        assertInvalid(() -> SearchCriteria.of(26.9, 75.8, 0.0, null, null, null, null, null, TODAY), "radiusKm");
        assertInvalid(() -> SearchCriteria.of(26.9, 75.8, -5.0, null, null, null, null, null, TODAY), "radiusKm");
    }

    @Test
    void theWindowMustRunForwardsAndNotExceedAYear() {
        assertThat(SearchCriteria.of(26.9, 75.8, null, TODAY, TODAY, null, null, null, TODAY).to()).isEqualTo(TODAY);
        assertInvalid(() -> SearchCriteria.of(26.9, 75.8, null, TODAY, TODAY.minusDays(1), null, null, null, TODAY),
                "to must not be before from");
        assertInvalid(() -> SearchCriteria.of(26.9, 75.8, null, TODAY, TODAY.plusDays(367), null, null, null, TODAY),
                "at most 366 days apart");
    }

    @Test
    void stylesAreCheckedAndNormalised() {
        SearchCriteria criteria = SearchCriteria.of(
                26.9, 75.8, null, null, null, List.of("hiking", " FOODIE ", "Hiking", ""), null, null, TODAY);

        assertThat(criteria.styles()).containsExactly("FOODIE", "HIKING");
        assertThat(criteria.hasStyles()).isTrue();
        assertInvalid(() -> SearchCriteria.of(26.9, 75.8, null, null, null, List.of("SURFING"), null, null, TODAY),
                "Unknown style: SURFING");
    }

    @Test
    void theLimitIsCappedAndMustBePositive() {
        assertThat(SearchCriteria.of(26.9, 75.8, null, null, null, null, null, 7, TODAY).limit()).isEqualTo(7);
        assertThat(SearchCriteria.of(26.9, 75.8, null, null, null, null, null, 1000, TODAY).limit()).isEqualTo(50);
        assertInvalid(() -> SearchCriteria.of(26.9, 75.8, null, null, null, null, null, 0, TODAY), "limit must be at least 1");
    }

    @Test
    void theCursorIsDecoded() {
        Cursor cursor = new Cursor(TODAY.plusDays(3), UUID.randomUUID());

        assertThat(SearchCriteria.of(26.9, 75.8, null, null, null, null, cursor.encode(), null, TODAY).cursor())
                .isEqualTo(cursor);
        assertThat(SearchCriteria.of(26.9, 75.8, null, null, null, null, "  ", null, TODAY).cursor()).isNull();
        assertInvalid(() -> SearchCriteria.of(26.9, 75.8, null, null, null, null, "garbage!", null, TODAY),
                "cursor is not valid");
    }

    private static SearchCriteria of(Double lat, Double lng) {
        return SearchCriteria.of(lat, lng, null, null, null, null, null, null, TODAY);
    }

    private static void assertInvalid(Runnable action, String messagePart) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"))
                .hasMessageContaining(messagePart);
    }
}
