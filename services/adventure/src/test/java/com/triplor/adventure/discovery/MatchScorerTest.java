package com.triplor.adventure.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.triplor.adventure.discovery.DiscoveryDtos.MatchScore;
import org.junit.jupiter.api.Test;

class MatchScorerTest {

    /** My trip: ten days, 1 to 10 March. */
    static final LocalDate START = LocalDate.parse("2030-03-01");
    static final LocalDate END = LocalDate.parse("2030-03-10");

    @Test
    void theSameTripAtTheSameSpotScoresOne() {
        MatchScore score = MatchScorer.score(
                START, END, List.of("CULTURE", "FOODIE"), START, END, List.of("FOODIE", "CULTURE"), 0, 50);

        assertThat(score).isEqualTo(new MatchScore(1.0, 1.0, 1.0, 1.0));
    }

    @Test
    void theTotalIsTheWeightedSumOfTheComponents() {
        // Half my days, one of two styles, halfway to the edge of the radius.
        MatchScore score = MatchScorer.score(
                START, END, List.of("CULTURE", "FOODIE"),
                LocalDate.parse("2030-03-06"), LocalDate.parse("2030-03-20"), List.of("CULTURE"), 25, 50);

        assertThat(score.dateOverlap()).isEqualTo(0.5);
        assertThat(score.styleSimilarity()).isEqualTo(0.5);
        assertThat(score.proximity()).isEqualTo(0.5);
        assertThat(score.total()).isEqualTo(0.5); // 0.5 * 0.5 + 0.3 * 0.5 + 0.2 * 0.5
    }

    @Test
    void theWeightsAddUpToOne() {
        assertThat(MatchScorer.DATE_WEIGHT + MatchScorer.STYLE_WEIGHT + MatchScorer.PROXIMITY_WEIGHT).isEqualTo(1.0);
    }

    @Test
    void dateOverlapCountsSharedDaysAsAShareOfMyTrip() {
        // Their trip covers mine completely.
        assertThat(MatchScorer.dateOverlap(START, END, START.minusDays(5), END.plusDays(5))).isEqualTo(1.0);
        // Only my last day is shared: one day of ten.
        assertThat(MatchScorer.dateOverlap(START, END, END, END.plusDays(4))).isEqualTo(0.1);
        // Their trip lies inside mine: three days of ten.
        assertThat(MatchScorer.dateOverlap(START, END, START.plusDays(2), START.plusDays(4))).isEqualTo(0.3);
        // No day in common.
        assertThat(MatchScorer.dateOverlap(START, END, END.plusDays(1), END.plusDays(9))).isZero();
    }

    @Test
    void styleSimilarityIsSharedOverAllStyles() {
        assertThat(MatchScorer.styleSimilarity(List.of("HIKING"), List.of("HIKING"))).isEqualTo(1.0);
        assertThat(MatchScorer.styleSimilarity(List.of("HIKING", "FOODIE"), List.of("FOODIE", "CULTURE")))
                .isEqualTo(1.0 / 3);
        assertThat(MatchScorer.styleSimilarity(List.of("HIKING"), List.of("RELAXING"))).isZero();
        assertThat(MatchScorer.styleSimilarity(List.of(), List.of())).isZero();
    }

    @Test
    void proximityFallsFromOneToZeroAcrossTheRadius() {
        assertThat(MatchScorer.proximity(0, 50)).isEqualTo(1.0);
        assertThat(MatchScorer.proximity(10, 50)).isEqualTo(0.8);
        assertThat(MatchScorer.proximity(50, 50)).isZero();
        // Rounding can put a candidate a hair outside the radius; never negative.
        assertThat(MatchScorer.proximity(50.1, 50)).isZero();
    }

    @Test
    void datesWeighMoreThanStylesAndStylesMoreThanDistance() {
        MatchScore sameDatesOnly = MatchScorer.score(START, END, List.of("HIKING"), START, END, List.of("RELAXING"), 50, 50);
        MatchScore sameStylesOnly = MatchScorer.score(START, END, List.of("HIKING"), END, END, List.of("HIKING"), 50, 50);
        MatchScore sameSpotOnly = MatchScorer.score(START, END, List.of("HIKING"), END, END, List.of("RELAXING"), 0, 50);

        assertThat(sameDatesOnly.total()).isGreaterThan(sameStylesOnly.total());
        assertThat(sameStylesOnly.total()).isGreaterThan(sameSpotOnly.total());
    }
}
