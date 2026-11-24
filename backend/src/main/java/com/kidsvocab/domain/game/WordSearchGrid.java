package com.kidsvocab.domain.game;

import java.util.List;

/**
 * A stored word-search grid.
 *
 * @param rows    {@code size} rows of {@code size} lower-case letters
 * @param wordIds the words hidden in it (week words or task words), the others did not fit
 */
public record WordSearchGrid(int size, List<String> rows, List<Long> wordIds) {

    public WordSearchGrid {
        rows = List.copyOf(rows);
        wordIds = List.copyOf(wordIds);
    }
}
