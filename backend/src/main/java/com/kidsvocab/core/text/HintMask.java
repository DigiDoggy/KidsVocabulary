package com.kidsvocab.core.text;

import java.util.SplittableRandom;

/**
 * Builds the hint mask for a word: letters are revealed in a given order (random positions in
 * training), hidden letters are shown as {@link #HIDDEN}; non-letters (spaces, hyphens, apostrophes)
 * are always visible.
 */
public final class HintMask {

    public static final char HIDDEN = '_';

    private HintMask() {
    }

    public static int letterCount(String word) {
        return (int) word.codePoints().filter(Character::isLetter).count();
    }

    /** At most {@code letters - 1} letters can be revealed, so a hint never gives the whole word away. */
    public static int maxRevealable(String word) {
        return Math.max(0, letterCount(word) - 1);
    }

    /**
     * A random order in which the letters of the word get revealed: a permutation of the letter
     * indices {@code 0 .. letterCount - 1}. The same seed always gives the same order.
     */
    public static int[] revealOrder(String word, long seed) {
        int[] order = new int[letterCount(word)];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        SplittableRandom rnd = new SplittableRandom(seed);
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        return order;
    }

    /**
     * @param order    letter indices in reveal order (see {@link #revealOrder})
     * @param revealed how many letters of {@code order} are shown
     */
    public static String mask(String word, int[] order, int revealed) {
        if (revealed < 0) {
            throw new IllegalArgumentException("revealed must not be negative: " + revealed);
        }
        boolean[] shown = new boolean[letterCount(word)];
        for (int i = 0; i < Math.min(revealed, order.length); i++) {
            shown[order[i]] = true;
        }
        StringBuilder sb = new StringBuilder(word.length());
        int letter = 0;
        for (int cp : word.codePoints().toArray()) {
            if (!Character.isLetter(cp)) {
                sb.appendCodePoint(cp);
            } else if (shown[letter++]) {
                sb.appendCodePoint(cp);
            } else {
                sb.append(HIDDEN);
            }
        }
        return sb.toString();
    }
}
