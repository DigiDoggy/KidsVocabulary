package com.kidsvocab.core.suggestion;

/**
 * A word proposed by an external source (e.g. an AI model), before validation.
 *
 * @param source word in the child's native language
 * @param target translation in the target language
 * @param topic   topic as reported by the source; may be anything
 * @param english the English meaning, used to match the word with existing dictionary concepts;
 *                may be {@code null}
 * @param emoji   a picture for the word as proposed by the source; may be anything or {@code null}
 */
public record SuggestedWord(String source, String target, String topic, String english, String emoji) {

    public SuggestedWord(String source, String target, String topic, String english) {
        this(source, target, topic, english, null);
    }

    public SuggestedWord(String source, String target, String topic) {
        this(source, target, topic, null, null);
    }
}
