package com.kidsvocab.ai;

import com.kidsvocab.domain.child.Level;

import java.util.List;
import java.util.Set;

/**
 * Everything the model needs to propose words. Deliberately contains no personal data:
 * only an age in years, a level and languages.
 *
 * @param childAge           age in years, or {@code null} if unknown
 * @param topic              requested topic, or {@code null} to let the model choose from {@code topics}
 * @param topics             topics the model may use
 * @param count              number of words to ask for
 * @param excludeTargetWords target-language words that must not be suggested
 */
public record SuggestionRequest(Integer childAge, Level level, String topic, List<String> topics, int count,
                                String nativeLang, String targetLang, Set<String> excludeTargetWords) {

    public SuggestionRequest {
        topics = List.copyOf(topics);
        excludeTargetWords = Set.copyOf(excludeTargetWords);
    }
}
