package com.kidsvocab.core.week;

import com.kidsvocab.core.scoring.ScoringPolicy;
import com.kidsvocab.core.scoring.WordScore;
import com.kidsvocab.core.training.WordState;

import java.math.BigDecimal;
import java.util.List;

/**
 * Turns the first-pass word states into the week result. The same rule covers both endings:
 * <ul>
 *   <li>first pass completed — every word is solved and scored;</li>
 *   <li>week expired — solved words keep their points, unsolved or unanswered words get 0 and
 *       {@code UNLEARNED}. A week that was never started is passed as {@link WordState#notStarted}.</li>
 * </ul>
 */
public final class WeekSummarizer {

    private WeekSummarizer() {
    }

    public static WeekResult summarize(List<WordState> states) {
        if (states.isEmpty()) {
            throw new IllegalArgumentException("a week has at least one word");
        }
        List<WordResult> words = states.stream().map(WeekSummarizer::toResult).toList();
        BigDecimal points = words.stream().map(WordResult::points).reduce(ScoringPolicy.ZERO, BigDecimal::add);
        return new WeekResult(words, points, ScoringPolicy.maxPoints(words.size()));
    }

    private static WordResult toResult(WordState state) {
        WordScore score = ScoringPolicy.score(state.solvedRound(), state.hintUsed());
        return new WordResult(state.wordId(), state.solvedRound(), state.hintUsed(), state.problematic(),
                score.points(), score.outcome());
    }
}
