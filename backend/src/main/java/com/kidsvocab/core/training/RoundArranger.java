package com.kidsvocab.core.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Orders the words of a round. Both groups are shuffled; problematic words are spread between
 * regular words so that they are neither first nor last and never adjacent. When there are too few
 * regular words the constraint is relaxed step by step:
 * <ol>
 *   <li>{@code P <= N - 1}: only inner gaps, one problematic word per gap;</li>
 *   <li>{@code P <= N + 1}: edges allowed, still one per gap;</li>
 *   <li>otherwise: problematic words are distributed over all gaps as evenly as possible.</li>
 * </ol>
 */
public final class RoundArranger {

    private RoundArranger() {
    }

    public static <T> List<T> arrange(List<T> regular, List<T> problematic, RandomGenerator rnd) {
        List<T> n = shuffled(regular, rnd);
        List<T> p = shuffled(problematic, rnd);
        if (p.isEmpty()) {
            return n;
        }
        if (n.isEmpty()) {
            return p;
        }

        int gaps = n.size() + 1;              // gap i = before n[i]; gap n.size() = after the last one
        int[] perGap = new int[gaps];
        int innerGaps = n.size() - 1;
        if (p.size() <= innerGaps) {
            pickDistinct(1, innerGaps, p.size(), rnd).forEach(g -> perGap[g] = 1);
        } else if (p.size() <= gaps) {
            pickDistinct(0, n.size(), p.size(), rnd).forEach(g -> perGap[g] = 1);
        } else {
            Arrays.fill(perGap, p.size() / gaps);
            pickDistinct(0, n.size(), p.size() % gaps, rnd).forEach(g -> perGap[g]++);
        }

        List<T> result = new ArrayList<>(n.size() + p.size());
        Iterator<T> pi = p.iterator();
        for (int gap = 0; gap < gaps; gap++) {
            for (int k = 0; k < perGap[gap]; k++) {
                result.add(pi.next());
            }
            if (gap < n.size()) {
                result.add(n.get(gap));
            }
        }
        return result;
    }

    /** Fisher–Yates shuffle into a new list. */
    static <T> List<T> shuffled(List<T> items, RandomGenerator rnd) {
        List<T> copy = new ArrayList<>(items);
        for (int i = copy.size() - 1; i > 0; i--) {
            Collections.swap(copy, i, rnd.nextInt(i + 1));
        }
        return copy;
    }

    private static List<Integer> pickDistinct(int fromInclusive, int toInclusive, int count, RandomGenerator rnd) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = fromInclusive; i <= toInclusive; i++) {
            candidates.add(i);
        }
        return shuffled(candidates, rnd).subList(0, count);
    }
}
