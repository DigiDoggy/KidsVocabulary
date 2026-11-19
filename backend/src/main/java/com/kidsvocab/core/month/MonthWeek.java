package com.kidsvocab.core.month;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A closed week as input for the monthly report. */
public record MonthWeek(int index, LocalDate startDate, BigDecimal points, BigDecimal maxPoints,
                        List<ConceptOutcome> words) {

    public MonthWeek {
        if (maxPoints.signum() <= 0) {
            throw new IllegalArgumentException("maxPoints must be positive");
        }
        words = List.copyOf(words);
    }
}
