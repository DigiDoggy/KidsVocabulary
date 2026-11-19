package com.kidsvocab.core.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Brings a typed answer to a canonical form so that comparison ignores case,
 * surrounding/duplicate whitespace and typographic variants of apostrophes and dashes.
 */
public final class AnswerNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private AnswerNormalizer() {
    }

    public static String normalize(String input, Locale locale) {
        Objects.requireNonNull(locale, "locale");
        if (input == null) {
            return "";
        }
        String s = Normalizer.normalize(input, Normalizer.Form.NFC)
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('`', '\'')
                .replace('–', '-')
                .replace('—', '-');
        s = WHITESPACE.matcher(s).replaceAll(" ").strip();
        return s.toLowerCase(locale);
    }
}
