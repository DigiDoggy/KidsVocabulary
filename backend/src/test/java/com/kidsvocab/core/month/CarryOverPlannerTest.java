package com.kidsvocab.core.month;

import com.kidsvocab.core.month.CarryOverPlanner.Candidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.kidsvocab.core.month.CarryOverPlanner.Reason.HARD;
import static com.kidsvocab.core.month.CarryOverPlanner.Reason.UNLEARNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CarryOverPlannerTest {

    @ParameterizedTest(name = "queue {0}, limit {1}, weeks left {2} -> {3}")
    @CsvSource({
            "0,  10, 4, 0",
            "6,  10, 4, 2",
            "4,  10, 3, 2",
            "2,  10, 2, 1",
            "1,  10, 1, 1",
            "20, 10, 4, 5",   // fits exactly under the half-limit cap
            "21, 10, 4, 6",   // does not fit: cap grows
            "50, 10, 4, 10",  // never more than the full limit
            "1,  1,  4, 1",   // half of 1 is 0, still room for one word
            "3,  10, 1, 3",
    })
    void quotaForTheNextWeek(int queue, int limit, int weeksLeft, int expected) {
        assertThat(CarryOverPlanner.quota(queue, limit, weeksLeft)).isEqualTo(expected);
    }

    @Test
    void spreadsTheQueueOverTheMonthWithinHalfTheLimit() {
        int queue = 6;
        int limit = 10;
        int placed = 0;
        for (int weeksLeft = 4; weeksLeft >= 1; weeksLeft--) {
            int take = CarryOverPlanner.quota(queue - placed, limit, weeksLeft);
            assertThat(take).isBetween(1, limit / 2);
            placed += take;
        }
        assertThat(placed).isEqualTo(queue);
    }

    @Test
    void overflowBeyondFourFullWeeksWaitsForTheNextMonth() {
        int queue = 45;
        int placed = 0;
        for (int weeksLeft = 4; weeksLeft >= 1; weeksLeft--) {
            placed += CarryOverPlanner.quota(queue - placed, 10, weeksLeft);
        }
        assertThat(placed).isEqualTo(40);
    }

    @Test
    void selectsUnlearnedBeforeHardKeepingQueueOrder() {
        List<Candidate> queue = List.of(
                new Candidate(1, HARD), new Candidate(2, UNLEARNED), new Candidate(3, HARD),
                new Candidate(4, UNLEARNED), new Candidate(5, HARD), new Candidate(6, HARD));

        List<Candidate> selected = CarryOverPlanner.select(queue, 10, 2);

        assertThat(selected).extracting(Candidate::conceptId).containsExactly(2L, 4L, 1L);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatIllegalArgumentException().isThrownBy(() -> CarryOverPlanner.quota(-1, 10, 4));
        assertThatIllegalArgumentException().isThrownBy(() -> CarryOverPlanner.quota(1, 0, 4));
        assertThatIllegalArgumentException().isThrownBy(() -> CarryOverPlanner.quota(1, 10, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> CarryOverPlanner.quota(1, 10, 5));
    }
}
