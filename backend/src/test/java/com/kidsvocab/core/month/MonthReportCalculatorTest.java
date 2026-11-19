package com.kidsvocab.core.month;

import com.kidsvocab.core.scoring.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.kidsvocab.core.scoring.Outcome.HARD;
import static com.kidsvocab.core.scoring.Outcome.LEARNED;
import static com.kidsvocab.core.scoring.Outcome.UNLEARNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MonthReportCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 2);

    @Test
    void buildsTheMonthlyReport() {
        List<MonthWeek> weeks = List.of(
                // given out of order on purpose
                week(3, "3.5", "4", w(30, LEARNED), w(31, UNLEARNED)),
                week(1, "4", "4", w(10, LEARNED), w(11, LEARNED)),
                week(2, "2.5", "4", w(20, HARD), w(21, LEARNED)),
                week(4, "2.5", "4", w(40, HARD), w(20, UNLEARNED)));

        MonthReport report = MonthReportCalculator.calculate(weeks);

        assertThat(report.totalPoints()).isEqualByComparingTo("12.5");
        assertThat(report.maxPoints()).isEqualByComparingTo("16");
        assertThat(report.percent()).isEqualByComparingTo("78.13");
        assertThat(report.result()).isEqualTo(MonthResult.GOOD);
        assertThat(report.weeks()).extracting(WeekStats::index).containsExactly(1, 2, 3, 4);
        // concept 20 was hard in week 2 and unlearned in week 4: it counts as unlearned only
        assertThat(report.unlearnedConcepts()).containsExactly(31L, 20L);
        assertThat(report.hardConcepts()).containsExactly(40L);
    }

    @Test
    void weekStatisticsCountOutcomes() {
        MonthReport report = MonthReportCalculator.calculate(List.of(
                week(1, "3", "6", w(1, LEARNED), w(2, HARD), w(3, UNLEARNED))));

        WeekStats stats = report.weeks().getFirst();
        assertThat(stats).isEqualTo(new WeekStats(1, START, new BigDecimal("3"), new BigDecimal("6"),
                new BigDecimal("50.00"), 1, 1, 1));
    }

    @ParameterizedTest(name = "{0} of {1} -> {2}")
    @CsvSource({
            "80,    100, REWARD",
            "100,   100, REWARD",
            "79.99, 100, GOOD",
            "60,    100, GOOD",
            "59.99, 100, REPEAT",
            "0,     100, REPEAT",
            "32,    40,  REWARD",
            "31.5,  40,  GOOD",
            "23.5,  40,  REPEAT",
    })
    void resultBandsUseExactValues(String total, String max, MonthResult expected) {
        assertThat(MonthReportCalculator.result(new BigDecimal(total), new BigDecimal(max))).isEqualTo(expected);
    }

    @Test
    void percentIsRoundedHalfUpToTwoDecimals() {
        assertThat(MonthReportCalculator.percent(new BigDecimal("2"), new BigDecimal("3")))
                .isEqualByComparingTo("66.67");
        assertThat(MonthReportCalculator.percent(new BigDecimal("79.995"), new BigDecimal("100")))
                .isEqualByComparingTo("80.00");
        // display may round up to 80.00 but the result is still decided on the exact value
        assertThat(MonthReportCalculator.result(new BigDecimal("79.995"), new BigDecimal("100")))
                .isEqualTo(MonthResult.GOOD);
    }

    @Test
    void rejectsEmptyMonthAndWeeksWithoutPoints() {
        assertThatIllegalArgumentException().isThrownBy(() -> MonthReportCalculator.calculate(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> week(1, "0", "0"));
    }

    private static MonthWeek week(int index, String points, String max, ConceptOutcome... words) {
        return new MonthWeek(index, START.plusWeeks(index - 1L), new BigDecimal(points), new BigDecimal(max),
                List.of(words));
    }

    private static ConceptOutcome w(long conceptId, Outcome outcome) {
        return new ConceptOutcome(conceptId, outcome);
    }
}
