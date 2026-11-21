package com.kidsvocab.core.suggestion;

/**
 * A suggested word that passed validation.
 *
 * @param source           native word, trimmed
 * @param target           translation, trimmed
 * @param sourceNormalized normalised native word
 * @param targetNormalized normalised translation
 * @param topic            a whitelisted topic, or {@code null}
 * @param englishNormalized normalised English meaning, or {@code null} if the source gave none
 */
public record ValidatedWord(String source, String target, String sourceNormalized, String targetNormalized,
                            String topic, String englishNormalized) {

    public ValidatedWord(String source, String target, String sourceNormalized, String targetNormalized,
                         String topic) {
        this(source, target, sourceNormalized, targetNormalized, topic, null);
    }
}
