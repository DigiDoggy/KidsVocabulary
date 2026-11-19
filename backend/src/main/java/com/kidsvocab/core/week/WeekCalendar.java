package com.kidsvocab.core.week;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Study weeks are calendar weeks, Monday to Sunday, in the parent's time zone. */
public final class WeekCalendar {

    private WeekCalendar() {
    }

    /** Monday of the week that contains {@code day}. */
    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Sunday of the week that starts on {@code monday}. */
    public static LocalDate weekEnd(LocalDate monday) {
        return requireMonday(monday).plusDays(6);
    }

    /** First instant after the week: next Monday 00:00 in the given zone. */
    public static Instant deadline(LocalDate monday, ZoneId zone) {
        return requireMonday(monday).plusWeeks(1).atStartOfDay(zone).toInstant();
    }

    public static boolean isOver(LocalDate monday, ZoneId zone, Instant now) {
        return !now.isBefore(deadline(monday, zone));
    }

    private static LocalDate requireMonday(LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("week must start on Monday: " + date);
        }
        return date;
    }
}
