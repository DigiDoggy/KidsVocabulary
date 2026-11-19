package com.kidsvocab.core.week;

import com.kidsvocab.core.scoring.Outcome;
import com.kidsvocab.core.training.TrainingSession;
import com.kidsvocab.core.training.WordCard;
import com.kidsvocab.core.training.WordState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WeekSummarizerTest {

    @Test
    void completedWeekScoresEveryWord() {
        WeekResult result = WeekSummarizer.summarize(List.of(
                new WordState(1, 0, false, false, 1),
                new WordState(2, 1, true, false, 2),
                new WordState(3, 3, false, true, 4)));

        assertThat(result.words()).extracting(WordResult::points)
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(new java.math.BigDecimal("2"), new java.math.BigDecimal("0.5"),
                        new java.math.BigDecimal("0.5"));
        assertThat(result.words()).extracting(WordResult::outcome)
                .containsExactly(Outcome.LEARNED, Outcome.HARD, Outcome.HARD);
        assertThat(result.words().get(2)).isEqualTo(
                new WordResult(3, 4, false, true, new java.math.BigDecimal("0.5"), Outcome.HARD));
        assertThat(result.points()).isEqualByComparingTo("3");
        assertThat(result.maxPoints()).isEqualByComparingTo("6");
    }

    @Test
    void expiredWeekKeepsSolvedWordsAndZeroesTheRest() {
        WeekResult result = WeekSummarizer.summarize(List.of(
                new WordState(1, 0, false, false, 1),
                new WordState(2, 2, true, false, null),
                WordState.notStarted(3)));

        assertThat(result.words()).extracting(WordResult::outcome)
                .containsExactly(Outcome.LEARNED, Outcome.UNLEARNED, Outcome.UNLEARNED);
        assertThat(result.points()).isEqualByComparingTo("2");
        assertThat(result.maxPoints()).isEqualByComparingTo("6");
    }

    @Test
    void weekNeverStartedScoresZero() {
        WeekResult result = WeekSummarizer.summarize(List.of(WordState.notStarted(1), WordState.notStarted(2)));

        assertThat(result.points()).isEqualByComparingTo("0");
        assertThat(result.words()).allMatch(w -> w.outcome() == Outcome.UNLEARNED && w.solvedRound() == null);
        assertThat(WordState.notStarted(1).isSolved()).isFalse();
        assertThat(new WordState(1, 0, false, false, 1).isSolved()).isTrue();
    }

    @Test
    void summarizesAClosedTrainingSession() {
        Locale en = Locale.ENGLISH;
        TrainingSession session = TrainingSession.start(List.of(
                WordCard.of(1, "koer", "dog", List.of(), en),
                WordCard.of(2, "kass", "cat", List.of(), en)), 1L);
        long first = session.roundOrder().getFirst();
        session.submit(first, 1, first == 1 ? "dog" : "cat");
        session.close();

        WeekResult result = WeekSummarizer.summarize(session.wordStates());

        assertThat(result.points()).isEqualByComparingTo("2");
        assertThat(result.words()).filteredOn(w -> w.wordId() == first)
                .extracting(WordResult::outcome).containsExactly(Outcome.LEARNED);
    }

    @Test
    void weekNeedsWords() {
        assertThatIllegalArgumentException().isThrownBy(() -> WeekSummarizer.summarize(List.of()));
    }
}
