package com.kidsvocab.core.text;

/**
 * Builds the hint mask for a word: letters are revealed left to right, hidden letters are shown as
 * {@link #HIDDEN}; non-letters (spaces, hyphens, apostrophes) are always visible.
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

    public static String mask(String word, int revealed) {
        if (revealed < 0) {
            throw new IllegalArgumentException("revealed must not be negative: " + revealed);
        }
        StringBuilder sb = new StringBuilder(word.length());
        int shown = 0;
        for (int cp : word.codePoints().toArray()) {
            if (!Character.isLetter(cp)) {
                sb.appendCodePoint(cp);
            } else if (shown < revealed) {
                sb.appendCodePoint(cp);
                shown++;
            } else {
                sb.append(HIDDEN);
            }
        }
        return sb.toString();
    }
}
