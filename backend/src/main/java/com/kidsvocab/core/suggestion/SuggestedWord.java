package com.kidsvocab.core.suggestion;

/**
 * A word proposed by an external source (e.g. an AI model), before validation.
 *
 * @param source word in the child's native language
 * @param target translation in the target language
 * @param topic  topic as reported by the source; may be anything
 */
public record SuggestedWord(String source, String target, String topic) {
}
