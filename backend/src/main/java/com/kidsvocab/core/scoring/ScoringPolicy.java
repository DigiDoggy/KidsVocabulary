package com.kidsvocab.core.scoring;

import java.math.BigDecimal;

/**
 * Points for one word:
 * <pre>
 *   round solved | no hint | hint
 *   1            | 2       | 1
 *   2            | 1       | 0.5
 *   3+           | 0.5     | 0.5
 *   not solved   | 0       | 0
 * </pre>
 * A hint lowers the score by one level, never below 0.5.
 */
public final class ScoringPolicy {

    public static final BigDecimal MAX_PER_WORD = new BigDecimal("2.0");
    public static final BigDecimal ONE = new BigDecimal("1.0");
    public static final BigDecimal HALF = new BigDecimal("0.5");
    public static final BigDecimal ZERO = new BigDecimal("0.0");

    private static final BigDecimal[] LEVELS = {MAX_PER_WORD, ONE, HALF};

    private ScoringPolicy() {
    }

    /**
     * @param solvedRound round in which the word was spelled correctly, or {@code null} if never
     * @param hintUsed    whether a hint was used for this word in the session
     */
    public static WordScore score(Integer solvedRound, boolean hintUsed) {
        if (solvedRound == null) {
            return new WordScore(ZERO, Outcome.UNLEARNED);
        }
        if (solvedRound < 1) {
            throw new IllegalArgumentException("solvedRound must be >= 1: " + solvedRound);
        }
        int level = Math.min(solvedRound - 1 + (hintUsed ? 1 : 0), LEVELS.length - 1);
        BigDecimal points = LEVELS[level];
        Outcome outcome = points.compareTo(HALF) == 0 ? Outcome.HARD : Outcome.LEARNED;
        return new WordScore(points, outcome);
    }

    public static BigDecimal maxPoints(int wordCount) {
        if (wordCount < 0) {
            throw new IllegalArgumentException("wordCount must not be negative: " + wordCount);
        }
        return MAX_PER_WORD.multiply(BigDecimal.valueOf(wordCount));
    }
}
