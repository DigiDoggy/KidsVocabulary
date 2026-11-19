package com.kidsvocab.core.month;

import com.kidsvocab.core.scoring.Outcome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the monthly report: total points, mastery percentage, result band, per-week statistics and
 * the words that move to the next month (hard and unlearned).
 */
public final class MonthReportCalculator {

    static final BigDecimal REWARD_THRESHOLD = BigDecimal.valueOf(80);
    static final BigDecimal GOOD_THRESHOLD = BigDecimal.valueOf(60);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MonthReportCalculator() {
    }

    public static MonthReport calculate(List<MonthWeek> weeks) {
        if (weeks.isEmpty()) {
            throw new IllegalArgumentException("a month has at least one week");
        }
        List<MonthWeek> ordered = weeks.stream().sorted(Comparator.comparingInt(MonthWeek::index)).toList();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        Set<Long> hard = new LinkedHashSet<>();
        Set<Long> unlearned = new LinkedHashSet<>();
        for (MonthWeek week : ordered) {
            total = total.add(week.points());
            max = max.add(week.maxPoints());
            for (ConceptOutcome word : week.words()) {
                if (word.outcome() == Outcome.UNLEARNED) {
                    unlearned.add(word.conceptId());
                } else if (word.outcome() == Outcome.HARD) {
                    hard.add(word.conceptId());
                }
            }
        }
        hard.removeAll(unlearned);

        List<WeekStats> stats = ordered.stream().map(MonthReportCalculator::weekStats).toList();
        return new MonthReport(total, max, percent(total, max), result(total, max), stats,
                List.copyOf(hard), List.copyOf(unlearned));
    }

    /** Decided on exact values: total / max >= threshold / 100, i.e. total * 100 >= threshold * max. */
    static MonthResult result(BigDecimal total, BigDecimal max) {
        BigDecimal scaled = total.multiply(HUNDRED);
        if (scaled.compareTo(REWARD_THRESHOLD.multiply(max)) >= 0) {
            return MonthResult.REWARD;
        }
        if (scaled.compareTo(GOOD_THRESHOLD.multiply(max)) >= 0) {
            return MonthResult.GOOD;
        }
        return MonthResult.REPEAT;
    }

    static BigDecimal percent(BigDecimal points, BigDecimal max) {
        return points.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP);
    }

    private static WeekStats weekStats(MonthWeek week) {
        int learned = count(week, Outcome.LEARNED);
        int hard = count(week, Outcome.HARD);
        int unlearned = count(week, Outcome.UNLEARNED);
        return new WeekStats(week.index(), week.startDate(), week.points(), week.maxPoints(),
                percent(week.points(), week.maxPoints()), learned, hard, unlearned);
    }

    private static int count(MonthWeek week, Outcome outcome) {
        return (int) week.words().stream().filter(w -> w.outcome() == outcome).count();
    }
}
