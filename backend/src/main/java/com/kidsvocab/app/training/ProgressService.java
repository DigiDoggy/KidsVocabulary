package com.kidsvocab.app.training;

import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.app.week.WeekLifecycleService;
import com.kidsvocab.core.month.MonthReport;
import com.kidsvocab.core.scoring.ScoringPolicy;
import com.kidsvocab.core.training.TrainingSession;
import com.kidsvocab.core.training.WordState;
import com.kidsvocab.core.week.WeekCalendar;
import com.kidsvocab.core.week.WeekResult;
import com.kidsvocab.core.week.WeekSummarizer;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.dictionary.DictionaryQueries;
import com.kidsvocab.domain.study.SessionRecord;
import com.kidsvocab.domain.study.SessionRecordRepository;
import com.kidsvocab.domain.study.StudyMonth;
import com.kidsvocab.domain.study.StudyMonthRepository;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.StudyWeekRepository;
import com.kidsvocab.domain.study.WeekStatus;
import com.kidsvocab.domain.study.WeekWord;
import com.kidsvocab.domain.study.WeekWordRepository;
import com.kidsvocab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** What the child and the parent see about progress: home screen, months, reports, week statistics. */
@Service
public class ProgressService {

    private static final List<WeekStatus> RUNNING_OR_CLOSED =
            List.of(WeekStatus.ACTIVE, WeekStatus.COMPLETED, WeekStatus.EXPIRED);

    private final ChildRepository children;
    private final StudyWeekRepository weeks;
    private final WeekWordRepository words;
    private final StudyMonthRepository months;
    private final SessionRecordRepository sessions;
    private final SessionLoader loader;
    private final WeekLifecycleService lifecycle;
    private final DictionaryQueries dictionary;

    public ProgressService(ChildRepository children, StudyWeekRepository weeks, WeekWordRepository words,
                           StudyMonthRepository months, SessionRecordRepository sessions, SessionLoader loader,
                           WeekLifecycleService lifecycle, DictionaryQueries dictionary) {
        this.children = children;
        this.weeks = weeks;
        this.words = words;
        this.months = months;
        this.sessions = sessions;
        this.loader = loader;
        this.lifecycle = lifecycle;
        this.dictionary = dictionary;
    }

    /**
     * The child's home screen.
     *
     * @param week          this calendar week's set (running or already closed), or {@code null}
     * @param weekPoints    points of that week: final once closed, so far while running
     * @param firstPassOpen the scoring first pass has been started and is not finished
     * @param month         the current month, or {@code null} before the first week
     * @param monthPoints   points of the month's closed weeks
     */
    public record Home(Child child, StudyWeek week, BigDecimal weekPoints, BigDecimal weekMaxPoints,
                       boolean firstPassOpen, StudyMonth month, int weeksClosed, BigDecimal monthPoints,
                       BigDecimal monthMaxPoints) {
    }

    /** A month with its weeks, as the parent sees it. */
    public record MonthProgress(StudyMonth month, List<StudyWeek> weeks, BigDecimal points, BigDecimal maxPoints) {
    }

    /** A word of a report with its texts in the child's languages. */
    public record ReportWord(long conceptId, String prompt, String answer) {
    }

    public record Report(StudyMonth month, MonthReport report, List<ReportWord> hardWords,
                         List<ReportWord> unlearnedWords) {
    }

    /** Per-word statistics of a week. */
    public record WordStat(WeekWord word, int wrongRounds, Integer solvedRound, boolean hintUsed, boolean problematic,
                           BigDecimal points, String outcome) {
    }

    // --- home ------------------------------------------------------------------------------------

    @Transactional
    public Home home(CurrentUser user, Long childIdForParent) {
        Child child = user.isParent()
                ? ownChild(user, childIdForParent)
                : children.findById(user.childId()).filter(c -> !c.isArchived())
                        .orElseThrow(() -> AppException.notFound("Child"));
        lifecycle.refresh(child);
        LocalDate thisMonday = WeekCalendar.weekStart(lifecycle.today(child));
        Optional<StudyWeek> week = weeks.findByChildIdAndStatusIn(child.getId(), RUNNING_OR_CLOSED).stream()
                .filter(w -> w.getStartDate().equals(thisMonday))
                .findFirst();

        BigDecimal weekPoints = null;
        BigDecimal weekMax = null;
        boolean firstPassOpen = false;
        if (week.isPresent()) {
            StudyWeek w = week.get();
            if (w.getStatus().isClosed()) {
                weekPoints = w.getPoints();
                weekMax = w.getMaxPoints();
            } else {
                Optional<SessionRecord> firstPass = sessions.findByWeekIdAndKind(w.getId(), SessionRecord.Kind.FIRST_PASS);
                WeekResult live = WeekSummarizer.summarize(firstPassStates(w, child, firstPass));
                weekPoints = live.points();
                weekMax = live.maxPoints();
                firstPassOpen = firstPass.isPresent();
            }
        }

        Optional<StudyMonth> month = months.findByChildIdAndStatus(child.getId(), StudyMonth.Status.ACTIVE);
        List<StudyWeek> closed = month.map(m -> weeks.findByMonthIdOrderByWeekIndex(m.getId())).orElse(List.of())
                .stream().filter(w -> w.getStatus().isClosed()).toList();
        return new Home(child, week.orElse(null), weekPoints, weekMax, firstPassOpen, month.orElse(null),
                closed.size(), sum(closed, StudyWeek::getPoints), sum(closed, StudyWeek::getMaxPoints));
    }

    // --- months ----------------------------------------------------------------------------------

    @Transactional
    public List<StudyMonth> months(CurrentUser user, long childId) {
        Child child = ownChild(user, childId);
        lifecycle.refresh(child);
        return months.findByChildIdOrderBySeqNoDesc(child.getId());
    }

    @Transactional
    public MonthProgress month(CurrentUser user, long monthId) {
        StudyMonth month = ownMonth(user, monthId);
        lifecycle.refresh(ownChild(user, month.getChildId()));
        List<StudyWeek> monthWeeks = weeks.findByMonthIdOrderByWeekIndex(month.getId());
        List<StudyWeek> closed = monthWeeks.stream().filter(w -> w.getStatus().isClosed()).toList();
        return new MonthProgress(month, monthWeeks, sum(closed, StudyWeek::getPoints),
                sum(closed, StudyWeek::getMaxPoints));
    }

    /** Sets the reward agreed for the current month (creating the month if the child has not started yet). */
    @Transactional
    public StudyMonth setReward(CurrentUser user, long childId, String reward) {
        Child child = ownChild(user, childId);
        lifecycle.refresh(child);
        StudyMonth month = months.findByChildIdAndStatus(child.getId(), StudyMonth.Status.ACTIVE)
                .orElseGet(() -> months.save(new StudyMonth(child.getId(), nextSeqNo(child.getId()))));
        month.changeReward(reward == null || reward.isBlank() ? null : reward.strip());
        return month;
    }

    @Transactional(readOnly = true)
    public Report report(CurrentUser user, long monthId) {
        StudyMonth month = ownMonth(user, monthId);
        if (month.getStatus() != StudyMonth.Status.COMPLETED) {
            throw new AppException(Code.CONFLICT, "The month is not finished yet");
        }
        Child child = children.findById(month.getChildId()).orElseThrow();
        MonthReport report = month.getReport();
        return new Report(month, report, texts(report.hardConcepts(), child), texts(report.unlearnedConcepts(), child));
    }

    // --- week statistics -------------------------------------------------------------------------

    @Transactional
    public List<WordStat> weekStats(CurrentUser user, long weekId) {
        StudyWeek week = weeks.findById(weekId).orElseThrow(() -> AppException.notFound("Week"));
        Child child = ownChild(user, week.getChildId());
        lifecycle.refresh(child);
        week = weeks.findById(weekId).orElseThrow();
        Map<Long, WordState> states = firstPassStates(week, child,
                sessions.findByWeekIdAndKind(week.getId(), SessionRecord.Kind.FIRST_PASS)).stream()
                .collect(Collectors.toMap(WordState::wordId, Function.identity()));
        boolean closed = week.getStatus().isClosed();
        return words.findByWeekIdOrderById(week.getId()).stream().map(w -> {
            WordState s = states.get(w.getId());
            var live = ScoringPolicy.score(s.solvedRound(), s.hintUsed());
            return new WordStat(w, s.wrongCount(), s.solvedRound(), s.hintUsed(), s.problematic(),
                    closed ? w.getPoints() : live.points(),
                    closed ? w.getOutcome().name() : null);
        }).toList();
    }

    // --- internals -------------------------------------------------------------------------------

    private List<WordState> firstPassStates(StudyWeek week, Child child, Optional<SessionRecord> firstPass) {
        if (firstPass.isPresent()) {
            TrainingSession session = loader.load(firstPass.get(), child);
            return session.wordStates();
        }
        return words.findByWeekIdOrderById(week.getId()).stream().map(w -> WordState.notStarted(w.getId())).toList();
    }

    private List<ReportWord> texts(List<Long> conceptIds, Child child) {
        return conceptIds.stream()
                .map(id -> dictionary.card(id, child.getNativeLang(), child.getTargetLang())
                        .map(c -> new ReportWord(id, c.prompt(), c.primaryAnswer())))
                .flatMap(Optional::stream)
                .toList();
    }

    private static BigDecimal sum(List<StudyWeek> list, Function<StudyWeek, BigDecimal> field) {
        return list.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int nextSeqNo(long childId) {
        return months.findByChildIdOrderBySeqNoDesc(childId).stream()
                .max(Comparator.comparingInt(StudyMonth::getSeqNo))
                .map(m -> m.getSeqNo() + 1).orElse(1);
    }

    private StudyMonth ownMonth(CurrentUser user, long monthId) {
        StudyMonth month = months.findById(monthId).orElseThrow(() -> AppException.notFound("Month"));
        ownChild(user, month.getChildId());
        return month;
    }

    private Child ownChild(CurrentUser user, Long childId) {
        if (!user.isParent()) {
            throw new AppException(Code.FORBIDDEN, "Parent mode required");
        }
        if (childId == null) {
            throw AppException.notFound("Child");
        }
        return children.findByIdAndParentIdAndArchivedFalse(childId, user.parentId())
                .orElseThrow(() -> AppException.notFound("Child"));
    }
}
