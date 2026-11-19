package com.kidsvocab.core.week;

import com.kidsvocab.core.scoring.Outcome;

import java.math.BigDecimal;

/** Final first-pass result of one week word. */
public record WordResult(long wordId, Integer solvedRound, boolean hintUsed, boolean problematic,
                         BigDecimal points, Outcome outcome) {
}
