package com.kidsvocab.core.suggestion;

/**
 * A suggested word that passed validation.
 *
 * @param source           native word, trimmed
 * @param target           translation, trimmed
 * @param sourceNormalized normalised native word
 * @param targetNormalized normalised translation
 * @param topic            a whitelisted topic, or {@code null}
 */
public record ValidatedWord(String source, String target, String sourceNormalized, String targetNormalized,
                            String topic) {
}
