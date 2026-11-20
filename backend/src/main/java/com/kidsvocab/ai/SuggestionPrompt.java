package com.kidsvocab.ai;

import java.util.Map;
import java.util.stream.Collectors;

/** The instructions sent to every model, so all providers ask the same question. */
final class SuggestionPrompt {

    /** Upper bound for the exclusion list, to keep the prompt small. */
    static final int MAX_EXCLUDED = 300;

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "en", "English", "ru", "Russian", "et", "Estonian");

    private SuggestionPrompt() {
    }

    static String system() {
        return """
                You suggest vocabulary for children who learn to spell words in a foreign language.
                Reply with JSON only, exactly in this shape:
                {"words": [{"source": "...", "target": "...", "topic": "..."}]}
                Rules:
                - "source" is the word in the child's native language, "target" is its translation.
                - Common, concrete, child-friendly everyday words; nouns in the singular, verbs in the infinitive.
                - One word or a two-word phrase, lower case, no articles, no punctuation.
                - Each source word has one clear translation, and every target word appears only once.
                - Nothing about violence, weapons, alcohol, drugs, adult topics, brands or people's names.
                """;
    }

    static String user(SuggestionRequest r) {
        StringBuilder sb = new StringBuilder()
                .append("Native language: ").append(name(r.nativeLang())).append('\n')
                .append("Target language: ").append(name(r.targetLang())).append('\n')
                .append("Child: ").append(r.childAge() == null ? "school age" : r.childAge() + " years old")
                .append(", level ").append(r.level()).append(" (A0 = beginner)\n")
                .append("Number of words: ").append(r.count()).append('\n');
        if (r.topic() != null) {
            sb.append("Topic: ").append(r.topic()).append('\n');
        } else if (!r.topics().isEmpty()) {
            sb.append("Topics to choose from: ").append(String.join(", ", r.topics())).append('\n');
        }
        if (!r.excludeTargetWords().isEmpty()) {
            sb.append("Do not suggest these ").append(name(r.targetLang())).append(" words: ")
                    .append(r.excludeTargetWords().stream().sorted().limit(MAX_EXCLUDED)
                            .collect(Collectors.joining(", ")))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String name(String lang) {
        return LANGUAGE_NAMES.getOrDefault(lang, lang);
    }
}
