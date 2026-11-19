package com.kidsvocab.core.month;

import java.util.Comparator;
import java.util.List;

/**
 * Decides how many carried-over words go into the next week of a month.
 * <p>
 * Carried-over words count towards the weekly limit and are spread over the remaining weeks,
 * normally at most half of the limit per week. If the queue does not fit under that cap, the cap
 * grows up to the full limit; whatever still does not fit stays in the queue for the next month.
 * Unlearned words are taken before hard ones.
 */
public final class CarryOverPlanner {

    public static final int WEEKS_PER_MONTH = 4;

    public enum Reason { UNLEARNED, HARD }

    public record Candidate(long conceptId, Reason reason) {
    }

    private CarryOverPlanner() {
    }

    public static int quota(int queueSize, int weekLimit, int weeksLeft) {
        if (queueSize < 0) {
            throw new IllegalArgumentException("queueSize must not be negative: " + queueSize);
        }
        if (weekLimit < 1) {
            throw new IllegalArgumentException("weekLimit must be positive: " + weekLimit);
        }
        if (weeksLeft < 1 || weeksLeft > WEEKS_PER_MONTH) {
            throw new IllegalArgumentException("weeksLeft must be 1.." + WEEKS_PER_MONTH + ": " + weeksLeft);
        }
        int evenShare = Math.ceilDiv(queueSize, weeksLeft);
        int cap = weekLimit / 2;
        if (queueSize > cap * weeksLeft) {
            cap = Math.min(weekLimit, evenShare);
        }
        return Math.min(cap, evenShare);
    }

    /** Picks the carried-over words for the next week: unlearned first, then hard, queue order kept. */
    public static List<Candidate> select(List<Candidate> queue, int weekLimit, int weeksLeft) {
        int take = quota(queue.size(), weekLimit, weeksLeft);
        return queue.stream()
                .sorted(Comparator.comparing(Candidate::reason))
                .limit(take)
                .toList();
    }
}
