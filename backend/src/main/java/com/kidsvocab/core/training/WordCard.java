package com.kidsvocab.core.training;

import com.kidsvocab.core.text.AnswerNormalizer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A word as it is trained: the prompt in the child's native language and the accepted spellings
 * in the target language (already normalised).
 *
 * @param id              week word id
 * @param prompt          text shown to the child
 * @param primaryAnswer   main spelling, used for hints and shown after a mistake
 * @param acceptedAnswers normalised spellings that count as correct
 * @param targetLocale    locale of the target language, used for normalisation
 */
public record WordCard(long id, String prompt, String primaryAnswer, Set<String> acceptedAnswers,
                       Locale targetLocale) {

    public WordCard {
        requireText(prompt, "prompt");
        requireText(primaryAnswer, "primaryAnswer");
        Objects.requireNonNull(targetLocale, "targetLocale");
        acceptedAnswers = Set.copyOf(acceptedAnswers);
        if (acceptedAnswers.isEmpty()) {
            throw new IllegalArgumentException("acceptedAnswers must not be empty");
        }
    }

    /** Creates a card whose accepted answers are the primary answer plus non-blank extra answers. */
    public static WordCard of(long id, String prompt, String primaryAnswer, Collection<String> extraAnswers,
                              Locale targetLocale) {
        Set<String> accepted = new LinkedHashSet<>();
        accepted.add(AnswerNormalizer.normalize(primaryAnswer, targetLocale));
        for (String extra : extraAnswers) {
            String normalized = AnswerNormalizer.normalize(extra, targetLocale);
            if (!normalized.isEmpty()) {
                accepted.add(normalized);
            }
        }
        return new WordCard(id, prompt, primaryAnswer, accepted, targetLocale);
    }

    public boolean accepts(String answer) {
        return acceptedAnswers.contains(AnswerNormalizer.normalize(answer, targetLocale));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
