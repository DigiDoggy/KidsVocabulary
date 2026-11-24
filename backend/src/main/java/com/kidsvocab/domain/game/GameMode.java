package com.kidsvocab.domain.game;

/** The unscored game modes. The scored typing mode is the training itself. */
public enum GameMode {
    /** Pick the translation out of four. */
    CHOICE,
    /** Tap the letters of the word in the right order. */
    SCRAMBLE,
    /** Find the words in a grid of letters. */
    WORD_SEARCH
}
