package com.kidsvocab.core.text;

import java.util.regex.Pattern;

/**
 * The picture shown next to a word: exactly one emoji (one grapheme cluster, so ZWJ sequences, flags,
 * keycaps and skin tones count as one), nothing else.
 */
public final class Emoji {

    /** Longest accepted emoji in UTF-16 units; family ZWJ sequences need about a dozen. */
    public static final int MAX_LENGTH = 16;

    private static final Pattern ONE_GRAPHEME = Pattern.compile("\\X");
    private static final int KEYCAP = 0x20E3;
    private static final int EMOJI_STYLE = 0xFE0F;

    private Emoji() {
    }

    /**
     * Returns the trimmed emoji, or {@code null} if the value is blank or not a single emoji.
     */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String s = value.strip();
        if (s.isEmpty() || s.length() > MAX_LENGTH || !ONE_GRAPHEME.matcher(s).matches()) {
            return null;
        }
        return isPicture(s.codePoints().toArray()) ? s : null;
    }

    /**
     * Something drawn as a picture: an emoji-presentation character, an emoji followed by the emoji
     * variation selector, or a keycap. Plain text such as digits or the copyright sign is not.
     */
    private static boolean isPicture(int[] codePoints) {
        for (int i = 0; i < codePoints.length; i++) {
            int cp = codePoints[i];
            boolean styledEmoji = cp == EMOJI_STYLE && i > 0 && Character.isEmoji(codePoints[i - 1]);
            if (Character.isEmojiPresentation(cp) || styledEmoji || cp == KEYCAP) {
                return true;
            }
        }
        return false;
    }
}
