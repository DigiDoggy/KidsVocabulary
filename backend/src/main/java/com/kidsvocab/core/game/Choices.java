package com.kidsvocab.core.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;

/** Answer options for the "choose from 4" game: the right answer plus up to three distractors. */
public final class Choices {

    public static final int OPTIONS = 4;

    private Choices() {
    }

    /**
     * Picks distractors from the tiers in order (all of the first tier is used before the second one is
     * touched; within a tier the pick is random), skipping blanks, the answer itself and repeats
     * (compared case-insensitively), then shuffles the answer in.
     *
     * @param tiers candidate words in order of preference, e.g. the same list, learned words, the dictionary
     * @return between 1 and {@value #OPTIONS} options, the answer among them
     */
    public static List<String> options(String answer, List<List<String>> tiers, SplittableRandom random) {
        Set<String> seen = new HashSet<>(Set.of(key(answer)));
        List<String> options = new ArrayList<>();
        for (List<String> tier : tiers) {
            List<String> shuffled = new ArrayList<>(tier);
            shuffle(shuffled, random);
            for (String candidate : shuffled) {
                if (options.size() == OPTIONS - 1) {
                    break;
                }
                if (candidate != null && !candidate.isBlank() && seen.add(key(candidate))) {
                    options.add(candidate.strip());
                }
            }
        }
        options.add(answer);
        shuffle(options, random);
        return options;
    }

    private static String key(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }

    static <T> void shuffle(List<T> list, SplittableRandom random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
