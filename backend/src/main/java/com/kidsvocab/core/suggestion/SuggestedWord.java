package com.kidsvocab.core.suggestion;

/**
 * A word proposed by an external source (e.g. an AI model), before validation.
 *
 * @param source word in the child's native language
 * @param target translation in the target language
 * @param topic   topic as reported by the source; may be anything
 * @param english the English meaning, used to match the word with existing dictionary concepts;
 *                may be {@code null}
 */
public record SuggestedWord(String source, String target, String topic, String english) {

    public SuggestedWord(String source, String target, String topic) {
        this(source, target, topic, null);
    }
}
