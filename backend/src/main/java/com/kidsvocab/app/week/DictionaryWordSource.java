package com.kidsvocab.app.week;

import com.kidsvocab.domain.child.Level;
import com.kidsvocab.domain.dictionary.DictionaryQueries;
import com.kidsvocab.domain.dictionary.DictionaryQueries.SuggestionQuery;
import com.kidsvocab.domain.study.WordSource;
import org.springframework.stereotype.Component;

import java.util.List;

/** Picks random words from the shared dictionary, never repeating what the child already studied. */
@Component
public class DictionaryWordSource implements WordSuggestionSource {

    private final DictionaryQueries dictionary;

    public DictionaryWordSource(DictionaryQueries dictionary) {
        this.dictionary = dictionary;
    }

    @Override
    public List<Suggestion> suggest(Request request) {
        if (request.count() <= 0) {
            return List.of();
        }
        return dictionary.suggest(new SuggestionQuery(
                        request.nativeLang(), request.targetLang(), maxDifficulty(request.level()), request.topic(),
                        request.count(), request.excludeConcepts(), request.excludePrompts(), request.excludeAnswers()))
                .stream()
                .map(id -> new Suggestion(id, WordSource.DICTIONARY))
                .toList();
    }

    /** Beginners get short and medium words only; difficulty 3 is for longer words. */
    static int maxDifficulty(Level level) {
        return level == Level.A0 ? 2 : 3;
    }
}
