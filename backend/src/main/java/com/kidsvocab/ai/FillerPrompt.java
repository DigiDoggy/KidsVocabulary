package com.kidsvocab.ai;

import java.util.List;
import java.util.Map;

/** The instructions for filling a word-search grid, the same for every provider. */
final class FillerPrompt {

    static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("letters", Map.of("type", "STRING")),
            "required", List.of("letters"));

    private FillerPrompt() {
    }

    static String system() {
        return """
                You fill the empty cells of a word search puzzle for children.
                Reply with JSON only, exactly in this shape: {"letters": "..."}
                Rules:
                - Only lower-case letters of the given language's alphabet: no spaces, digits or punctuation.
                - Exactly the requested number of letters.
                - Make them look like the language: common letters and letter pairs more often, rare ones seldom,
                  so the hidden words do not stand out from random noise.
                - Never spell one of the hidden words, a real word for something unpleasant, or a rude word.
                """;
    }

    static String user(FillerRequest r) {
        return "Language: " + SuggestionPrompt.name(r.lang()) + '\n'
                + "Number of letters: " + r.count() + '\n'
                + "Hidden words: " + String.join(", ", r.words()) + '\n';
    }
}
