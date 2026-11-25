package com.kidsvocab.app.week;

import com.kidsvocab.core.month.CarryOverPlanner;
import com.kidsvocab.core.month.ConceptOutcome;
import com.kidsvocab.core.month.MonthReport;
import com.kidsvocab.core.month.MonthReportCalculator;
import com.kidsvocab.core.month.MonthWeek;
import com.kidsvocab.core.week.WeekCalendar;
import com.kidsvocab.core.week.WeekResult;
import com.kidsvocab.core.week.WeekSummarizer;
import com.kidsvocab.core.week.WordResult;
import com.kidsvocab.domain.account.ParentAccountRepository;
import com.kidsvocab.core.garden.Plant;
import com.kidsvocab.core.scoring.Outcome;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.study.GardenRepository;
import com.kidsvocab.domain.study.CarryOver;
import com.kidsvocab.domain.study.CarryOverRepository;
import com.kidsvocab.domain.study.SessionRecord;
import com.kidsvocab.domain.study.SessionRecordRepository;
import com.kidsvocab.domain.study.ChildWordHistory;
import com.kidsvocab.domain.study.StudyMonth;
import com.kidsvocab.domain.study.StudyMonthRepository;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.StudyWeekRepository;
import com.kidsvocab.domain.study.WeekStatus;
import com.kidsvocab.domain.study.WeekWord;
import com.kidsvocab.domain.study.WeekWordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Moves weeks and months through their life: scheduled weeks become active on their Monday,
 * active weeks expire after Sunday, and closing the fourth week closes the month, writes the report
 * and queues hard and unlearned words for the next month.
 */
@Service
public class WeekLifecycleService {

    private final StudyWeekRepository weeks;
    private final WeekWordRepository words;
    private final StudyMonthRepository months;
    private final CarryOverRepository carryOvers;
    private final ChildWordHistory history;
    private final ParentAccountRepository accounts;
    private final FirstPassProgress firstPass;
    private final SessionRecordRepository sessions;
    private final GardenRepository garden;
    private final ChildRepository children;
    private final Clock clock;

    public WeekLifecycleService(StudyWeekRepository weeks, WeekWordRepository words, StudyMonthRepository months,
                                CarryOverRepository carryOvers, ChildWordHistory history,
                                ParentAccountRepository accounts, FirstPassProgress firstPass,
                                SessionRecordRepository sessions, GardenRepository garden,
                                ChildRepository children, Clock clock) {
        this.weeks = weeks;
        this.words = words;
        this.months = months;
        this.carryOvers = carryOvers;
        this.history = history;
        this.accounts = accounts;
        this.firstPass = firstPass;
        this.sessions = sessions;
        this.garden = garden;
        this.children = children;
        this.clock = clock;
    }

    public ZoneId zoneOf(Child child) {
        return accounts.findById(child.getParentId())
                .map(a -> ZoneId.of(a.getTimezone()))
                .orElseThrow();
    }

    public LocalDate today(Child child) {
        return LocalDate.now(clock.withZone(zoneOf(child)));
    }

    /** Brings the child's weeks up to date with the current time. Safe to call repeatedly. */
    @Transactional
    public void refresh(Child child) {
        ZoneId zone = zoneOf(child);
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        expireOverdue(child, zone, now);
        for (StudyWeek week : weeks.findByChildIdAndStatusIn(child.getId(), List.of(WeekStatus.SCHEDULED))) {
            if (!week.getStartDate().isAfter(today)) {
                activate(week);
            }
        }
        expireOverdue(child, zone, now);   // a scheduled week that was missed entirely
        weeks.flush();
    }

    /** Starts a week: it joins the child's active month (created if needed) as its next week. */
    @Transactional
    public void activate(StudyWeek week) {
        StudyMonth month = months.findByChildIdAndStatus(week.getChildId(), StudyMonth.Status.ACTIVE)
                .orElseGet(() -> months.save(new StudyMonth(week.getChildId(), nextSeqNo(week.getChildId()))));
        int launched = (int) weeks.countByMonthId(month.getId());
        if (launched >= StudyMonth.WEEKS) {
            throw new IllegalStateException("month " + month.getId() + " already has " + launched + " weeks");
        }
        week.activate(month.getId(), launched + 1, clock.instant());
        weeks.flush();
    }

    /** How many weeks are still free in the month that the child's next week would join. */
    @Transactional(readOnly = true)
    public int weeksLeftForNextWeek(long childId) {
        return months.findByChildIdAndStatus(childId, StudyMonth.Status.ACTIVE)
                .map(m -> (int) (StudyMonth.WEEKS - weeks.countByMonthId(m.getId())))
                .filter(left -> left > 0)
                .orElse(StudyMonth.WEEKS);
    }

    /**
     * Closes a week, records the result per word, plants every learned or hard word that is not in the
     * garden yet and, after the fourth week, closes the month.
     */
    @Transactional
    public void close(StudyWeek week, WeekStatus closedStatus) {
        List<WeekWord> weekWords = words.findByWeekIdOrderById(week.getId());
        WeekResult result = WeekSummarizer.summarize(firstPass.wordStates(week, weekWords));
        Map<Long, WordResult> byWord = result.words().stream()
                .collect(Collectors.toMap(WordResult::wordId, Function.identity()));
        LocalDate closedOn = null;
        for (WeekWord word : weekWords) {
            WordResult r = byWord.get(word.getId());
            word.recordResult(r.solvedRound(), r.hintUsed(), r.problematic(), r.points(), r.outcome());
            history.record(week.getChildId(), word.getConceptId(), r.outcome(), week.getId());
            if (r.outcome() != Outcome.UNLEARNED) {
                if (closedOn == null) {
                    closedOn = today(children.findById(week.getChildId()).orElseThrow());
                }
                garden.plantIfNew(week.getChildId(), word.getConceptId(),
                        Plant.seed(ThreadLocalRandom.current().nextInt(Plant.KINDS), closedOn), week.getId());
            }
        }
        week.close(closedStatus, result.points(), result.maxPoints(), clock.instant());
        sessions.findByWeekIdAndKind(week.getId(), SessionRecord.Kind.FIRST_PASS).ifPresent(SessionRecord::closeIfOpen);
        weeks.flush();
        if (week.getWeekIndex() == StudyMonth.WEEKS) {
            closeMonth(months.findById(week.getMonthId()).orElseThrow());
        }
    }

    private void expireOverdue(Child child, ZoneId zone, Instant now) {
        for (StudyWeek week : weeks.findByChildIdAndStatusIn(child.getId(), List.of(WeekStatus.ACTIVE))) {
            if (WeekCalendar.isOver(week.getStartDate(), zone, now)) {
                close(week, WeekStatus.EXPIRED);
            }
        }
    }

    private void closeMonth(StudyMonth month) {
        List<MonthWeek> monthWeeks = weeks.findByMonthIdOrderByWeekIndex(month.getId()).stream()
                .map(w -> new MonthWeek(w.getWeekIndex(), w.getStartDate(), w.getPoints(), w.getMaxPoints(),
                        words.findByWeekIdOrderById(w.getId()).stream()
                                .map(word -> new ConceptOutcome(word.getConceptId(), word.getOutcome()))
                                .toList()))
                .toList();
        MonthReport report = MonthReportCalculator.calculate(monthWeeks);
        month.complete(report, clock.instant());
        months.flush();   // the next ACTIVE month may only be inserted after this one is COMPLETED

        queue(month, report.unlearnedConcepts(), CarryOverPlanner.Reason.UNLEARNED);
        queue(month, report.hardConcepts(), CarryOverPlanner.Reason.HARD);
        months.save(new StudyMonth(month.getChildId(), month.getSeqNo() + 1));
    }

    private void queue(StudyMonth month, List<Long> conceptIds, CarryOverPlanner.Reason reason) {
        for (Long conceptId : conceptIds) {
            if (!carryOvers.existsByChildIdAndConceptIdAndFromMonthId(month.getChildId(), conceptId, month.getId())) {
                carryOvers.save(new CarryOver(month.getChildId(), conceptId, month.getId(), reason));
            }
        }
    }

    private int nextSeqNo(long childId) {
        return months.findByChildIdOrderBySeqNoDesc(childId).stream()
                .findFirst().map(m -> m.getSeqNo() + 1).orElse(1);
    }
}
