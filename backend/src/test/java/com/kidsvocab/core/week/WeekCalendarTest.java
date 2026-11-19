package com.kidsvocab.core.week;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WeekCalendarTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    @Test
    void weekStartsOnMonday() {
        assertThat(WeekCalendar.weekStart(LocalDate.of(2026, 11, 18))).isEqualTo(MONDAY);
        assertThat(WeekCalendar.weekStart(MONDAY)).isEqualTo(MONDAY);
        assertThat(WeekCalendar.weekStart(LocalDate.of(2026, 11, 22))).isEqualTo(MONDAY);
    }

    @Test
    void weekEndsOnSunday() {
        assertThat(WeekCalendar.weekEnd(MONDAY)).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void deadlineIsNextMondayMidnightInParentsZone() {
        assertThat(WeekCalendar.deadline(MONDAY, TALLINN)).isEqualTo(Instant.parse("2026-11-22T22:00:00Z"));
    }

    @Test
    void deadlineFollowsDaylightSavingChange() {
        // Summer time starts on Sunday 2026-03-29, so the next Monday begins at UTC+3.
        assertThat(WeekCalendar.deadline(LocalDate.of(2026, 3, 23), TALLINN))
                .isEqualTo(Instant.parse("2026-03-29T21:00:00Z"));
    }

    @Test
    void weekIsOverFromTheDeadlineOn() {
        Instant deadline = WeekCalendar.deadline(MONDAY, TALLINN);

        assertThat(WeekCalendar.isOver(MONDAY, TALLINN, deadline.minusSeconds(1))).isFalse();
        assertThat(WeekCalendar.isOver(MONDAY, TALLINN, deadline)).isTrue();
    }

    @Test
    void rejectsWeeksNotStartingOnMonday() {
        LocalDate tuesday = MONDAY.plusDays(1);

        assertThatIllegalArgumentException().isThrownBy(() -> WeekCalendar.weekEnd(tuesday));
        assertThatIllegalArgumentException().isThrownBy(() -> WeekCalendar.deadline(tuesday, TALLINN));
    }
}
