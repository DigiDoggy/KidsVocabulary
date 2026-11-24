package com.kidsvocab.ai;

import java.util.List;

/**
 * Letters for the free cells of a word-search grid.
 *
 * @param lang  the grid's language
 * @param count number of letters wanted
 * @param words the hidden words, which the filler must not spell again
 */
public record FillerRequest(String lang, int count, List<String> words) {

    public FillerRequest {
        words = List.copyOf(words);
    }
}
