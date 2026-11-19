package com.kidsvocab.core.training;

/**
 * Snapshot of a word's progress in a session.
 *
 * @param wordId      week word id
 * @param wrongCount  number of rounds in which the word was spelled wrong
 * @param hintUsed    a hint was used for this word at least once
 * @param problematic the word reached the problematic threshold
 * @param solvedRound round in which it was spelled correctly, or {@code null}
 */
public record WordState(long wordId, int wrongCount, boolean hintUsed, boolean problematic, Integer solvedRound) {

    /** State of a word in a week whose first pass was never started. */
    public static WordState notStarted(long wordId) {
        return new WordState(wordId, 0, false, false, null);
    }

    public boolean isSolved() {
        return solvedRound != null;
    }
}
