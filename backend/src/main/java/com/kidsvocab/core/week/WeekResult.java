package com.kidsvocab.core.week;

import java.math.BigDecimal;
import java.util.List;

public record WeekResult(List<WordResult> words, BigDecimal points, BigDecimal maxPoints) {

    public WeekResult {
        words = List.copyOf(words);
    }
}
