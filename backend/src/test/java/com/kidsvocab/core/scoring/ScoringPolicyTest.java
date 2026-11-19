package com.kidsvocab.core.scoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ScoringPolicyTest {

    @ParameterizedTest(name = "round {0}, hint {1} -> {2} {3}")
    @CsvSource({
            "1, false, 2.0, LEARNED",
            "1, true,  1.0, LEARNED",
            "2, false, 1.0, LEARNED",
            "2, true,  0.5, HARD",
            "3, false, 0.5, HARD",
            "3, true,  0.5, HARD",
            "4, false, 0.5, HARD",
            "12, true, 0.5, HARD",
    })
    void scoresBySolvedRoundAndHint(int round, boolean hint, String points, Outcome outcome) {
        WordScore score = ScoringPolicy.score(round, hint);

        assertThat(score.points()).isEqualByComparingTo(points);
        assertThat(score.outcome()).isEqualTo(outcome);
    }

    @Test
    void unsolvedWordScoresZero() {
        assertThat(ScoringPolicy.score(null, false)).isEqualTo(new WordScore(ScoringPolicy.ZERO, Outcome.UNLEARNED));
        assertThat(ScoringPolicy.score(null, true).points()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsImpossibleRound() {
        assertThatIllegalArgumentException().isThrownBy(() -> ScoringPolicy.score(0, false));
    }

    @Test
    void maximumIsTwoPointsPerWord() {
        assertThat(ScoringPolicy.maxPoints(10)).isEqualByComparingTo("20");
        assertThat(ScoringPolicy.maxPoints(0)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThatIllegalArgumentException().isThrownBy(() -> ScoringPolicy.maxPoints(-1));
    }
}
