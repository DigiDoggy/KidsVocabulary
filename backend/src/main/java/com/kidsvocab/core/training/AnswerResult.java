package com.kidsvocab.core.training;

/**
 * Outcome of one submitted answer.
 *
 * @param verdict           correct, wrong, or ignored (blank answer, not counted as an attempt)
 * @param correctAnswer     primary spelling, shown to the child after a mistake; {@code null} if ignored
 * @param becameProblematic the word has just reached the problematic threshold
 * @param roundFinished     this answer completed the round
 * @param sessionFinished   this answer completed the whole session
 */
public record AnswerResult(Verdict verdict, String correctAnswer, boolean becameProblematic,
                           boolean roundFinished, boolean sessionFinished) {

    public enum Verdict { CORRECT, WRONG, IGNORED }

    static AnswerResult ignored() {
        return new AnswerResult(Verdict.IGNORED, null, false, false, false);
    }

    public boolean isCorrect() {
        return verdict == Verdict.CORRECT;
    }
}
