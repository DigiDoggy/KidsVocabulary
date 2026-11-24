package com.kidsvocab.core.text;

import java.util.Map;

/** The lower-case letters of each supported language, used to fill word-search grids. */
public final class Alphabet {

    private static final String LATIN = "abcdefghijklmnopqrstuvwxyz";
    private static final Map<String, String> LETTERS = Map.of(
            "en", LATIN,
            "de", LATIN + "äöüß",
            "et", LATIN + "õäöüšž",
            "ru", cyrillic());

    private Alphabet() {
    }

    /**
     * @throws IllegalArgumentException for a language without a known alphabet
     */
    public static String of(String lang) {
        String letters = LETTERS.get(lang);
        if (letters == null) {
            throw new IllegalArgumentException("No alphabet for language " + lang);
        }
        return letters;
    }

    /** a..ya (0x0430..0x044F) and yo (0x0451). */
    private static String cyrillic() {
        StringBuilder sb = new StringBuilder();
        for (int cp = 0x0430; cp <= 0x044F; cp++) {
            sb.appendCodePoint(cp);
        }
        return sb.appendCodePoint(0x0451).toString();
    }
}
