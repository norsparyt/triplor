package com.triplor.adventure.discovery;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.triplor.adventure.discovery.DiscoveryDtos.MatchScore;

/**
 * Scores how well another adventure fits mine. A pure function: no database, no clock.
 *
 * <table>
 *   <caption>Components, each from 0 to 1</caption>
 *   <tr><th>Component</th><th>Weight</th><th>Definition</th></tr>
 *   <tr><td>Date overlap</td><td>0.5</td><td>days in common divided by the length of my trip</td></tr>
 *   <tr><td>Style similarity</td><td>0.3</td><td>shared styles divided by all styles across both trips</td></tr>
 *   <tr><td>Proximity</td><td>0.2</td><td>one minus distance divided by the search radius</td></tr>
 * </table>
 *
 * <p>The weights are a starting point chosen by judgement, not tuned on data.
 */
public final class MatchScorer {

    static final double DATE_WEIGHT = 0.5;
    static final double STYLE_WEIGHT = 0.3;
    static final double PROXIMITY_WEIGHT = 0.2;

    private MatchScorer() {
    }

    public static MatchScore score(LocalDate myStart, LocalDate myEnd, Collection<String> myStyles,
            LocalDate theirStart, LocalDate theirEnd, Collection<String> theirStyles,
            double distanceKm, double radiusKm) {
        double dateOverlap = dateOverlap(myStart, myEnd, theirStart, theirEnd);
        double styleSimilarity = styleSimilarity(myStyles, theirStyles);
        double proximity = proximity(distanceKm, radiusKm);
        double total = DATE_WEIGHT * dateOverlap + STYLE_WEIGHT * styleSimilarity + PROXIMITY_WEIGHT * proximity;
        return new MatchScore(round(total), round(dateOverlap), round(styleSimilarity), round(proximity));
    }

    /** Days both trips have in common, as a share of my trip. Both the first and the last day count. */
    static double dateOverlap(LocalDate myStart, LocalDate myEnd, LocalDate theirStart, LocalDate theirEnd) {
        LocalDate overlapStart = myStart.isAfter(theirStart) ? myStart : theirStart;
        LocalDate overlapEnd = myEnd.isBefore(theirEnd) ? myEnd : theirEnd;
        long daysInCommon = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
        if (daysInCommon <= 0) {
            return 0;
        }
        long myDays = ChronoUnit.DAYS.between(myStart, myEnd) + 1;
        return (double) daysInCommon / myDays;
    }

    /** The Jaccard index of the two style sets: size of the intersection over size of the union. */
    static double styleSimilarity(Collection<String> mine, Collection<String> theirs) {
        Set<String> union = new HashSet<>(mine);
        union.addAll(theirs);
        if (union.isEmpty()) {
            return 0;
        }
        Set<String> shared = new HashSet<>(mine);
        shared.retainAll(theirs);
        return (double) shared.size() / union.size();
    }

    /** 1 at the same spot, falling in a straight line to 0 at the edge of the radius. */
    static double proximity(double distanceKm, double radiusKm) {
        return Math.max(0, Math.min(1, 1 - distanceKm / radiusKm));
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
