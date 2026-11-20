package com.kidsvocab.app.week;

import com.kidsvocab.domain.child.Level;
import com.kidsvocab.domain.study.WordSource;

import java.util.List;
import java.util.Set;

/**
 * Proposes new words for a weekly set. Implementations return concepts that already exist in the
 * dictionary (an AI-backed source saves new words first), so the caller can add them by id.
 */
public interface WordSuggestionSource {

    record Request(long parentId, Integer childAge, Level level, String nativeLang, String targetLang, String topic,
                   int count, Set<Long> excludeConcepts, Set<String> excludePrompts, Set<String> excludeAnswers) {
    }

    record Suggestion(long conceptId, WordSource source) {
    }

    List<Suggestion> suggest(Request request);
}
