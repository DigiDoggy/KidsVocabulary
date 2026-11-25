package com.kidsvocab.core.garden;

import com.kidsvocab.core.training.WordState;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A learned word as a plant in the child's garden. It grows one stage with every successful review
 * after a doubling wait (1, 2, 4, 8, then 16 weeks for upkeep) and never shrinks or dies: a plant
 * that is overdue only looks thirsty.
 *
 * @param kind           which drawing (0 .. {@link #KINDS} − 1), picked once when the word is planted
 * @param stage          how far it has grown
 * @param intervalWeeks  the wait after the next successful review is based on this:
 *                       a new plant has 1, a failed review sets 0
 * @param nextReviewOn   the day the plant wants water again
 */
public record Plant(int kind, GrowthStage stage, int intervalWeeks, LocalDate nextReviewOn) {

    /** Apple, cherry and pear trees, sunflower, tulip, cactus, rose, daisy. */
    public static final int KINDS = 8;
    public static final int FIRST_WAIT_WEEKS = 1;
    public static final int MAX_WAIT_WEEKS = 16;

    public Plant {
        if (kind < 0 || kind >= KINDS) {
            throw new IllegalArgumentException("unknown plant kind " + kind);
        }
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(nextReviewOn, "nextReviewOn");
        if (intervalWeeks < 0 || intervalWeeks > MAX_WAIT_WEEKS) {
            throw new IllegalArgumentException("interval out of range: " + intervalWeeks);
        }
    }

    /** A freshly learned word: a seed that wants its first water a week later. */
    public static Plant seed(int kind, LocalDate learnedOn) {
        return new Plant(kind, GrowthStage.SEED, FIRST_WAIT_WEEKS, learnedOn.plusWeeks(FIRST_WAIT_WEEKS));
    }

    /** Whether a review of the word went well enough to grow: right in round 1, no hint. */
    public static boolean succeeded(WordState state) {
        return Integer.valueOf(1).equals(state.solvedRound()) && !state.hintUsed();
    }

    /**
     * The plant after a review on {@code today}: on success one stage up and a doubled wait; on failure
     * the same stage, due again tomorrow, and the next success waits only one week.
     */
    public Plant reviewed(boolean success, LocalDate today) {
        if (!success) {
            return new Plant(kind, stage, 0, today.plusDays(1));
        }
        int wait = Math.max(FIRST_WAIT_WEEKS, Math.min(2 * intervalWeeks, MAX_WAIT_WEEKS));
        return new Plant(kind, stage.next(), wait, today.plusWeeks(wait));
    }

    /** Wants water today or earlier. */
    public boolean isDue(LocalDate today) {
        return !nextReviewOn.isAfter(today);
    }

    /** Was not watered on its day: shown drooping until a successful review. */
    public boolean isThirsty(LocalDate today) {
        return nextReviewOn.isBefore(today);
    }
}
