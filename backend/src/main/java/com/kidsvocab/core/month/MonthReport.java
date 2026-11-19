package com.kidsvocab.core.month;

import java.math.BigDecimal;
import java.util.List;

/**
 * Monthly summary.
 *
 * @param percent           mastery percentage, rounded to 2 decimals for display
 *                          (the result is decided on exact values)
 * @param hardConcepts      concepts with outcome HARD (and not UNLEARNED elsewhere in the month)
 * @param unlearnedConcepts concepts with outcome UNLEARNED
 */
public record MonthReport(BigDecimal totalPoints, BigDecimal maxPoints, BigDecimal percent, MonthResult result,
                          List<WeekStats> weeks, List<Long> hardConcepts, List<Long> unlearnedConcepts) {

    public MonthReport {
        weeks = List.copyOf(weeks);
        hardConcepts = List.copyOf(hardConcepts);
        unlearnedConcepts = List.copyOf(unlearnedConcepts);
    }
}
