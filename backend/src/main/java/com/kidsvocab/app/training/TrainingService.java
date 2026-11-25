package com.kidsvocab.app.training;

import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.app.garden.GardenService;
import com.kidsvocab.app.week.WeekLifecycleService;
import com.kidsvocab.core.training.AnswerResult;
import com.kidsvocab.core.training.CurrentItem;
import com.kidsvocab.core.training.SessionNotActiveException;
import com.kidsvocab.core.training.TrainingConflictException;
import com.kidsvocab.core.training.TrainingSession;
import com.kidsvocab.core.week.WeekResult;
import com.kidsvocab.core.week.WeekSummarizer;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.study.SessionEvent;
import com.kidsvocab.domain.study.SessionEventRepository;
import com.kidsvocab.domain.study.SessionRecord;
import com.kidsvocab.domain.study.SessionRecordRepository;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.StudyWeekRepository;
import com.kidsvocab.domain.study.WeekStatus;
import com.kidsvocab.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Training runs through a week's words. The first run of an active week is the scoring first pass;
 * every other run is practice. Finishing the first pass closes the week right away. A garden review
 * belongs to the child instead of a week; finishing it grows the plants.
 */
@Service
public class TrainingService {

    private final SessionRecordRepository sessions;
    private final SessionEventRepository events;
    private final SessionLoader loader;
    private final StudyWeekRepository weeks;
    private final ChildRepository children;
    private final WeekLifecycleService lifecycle;
    private final GardenService garden;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TrainingService(SessionRecordRepository sessions, SessionEventRepository events, SessionLoader loader,
                           StudyWeekRepository weeks, ChildRepository children, WeekLifecycleService lifecycle,
                           GardenService garden, Clock clock) {
        this.sessions = sessions;
        this.events = events;
        this.loader = loader;
        this.weeks = weeks;
        this.children = children;
        this.lifecycle = lifecycle;
        this.garden = garden;
        this.clock = clock;
    }

    /** Pictures of a session's words by week word id (the caller has already been allowed to see it). */
    @Transactional(readOnly = true)
    public Map<Long, String> emojis(SessionRecord record) {
        return loader.emojis(record);
    }

    /** A session together with its rebuilt engine state. */
    public record SessionView(SessionRecord record, TrainingSession session) {

        public Optional<CurrentItem> current() {
            return session.current();
        }

        /** Points as they stand now; unsolved words count 0. */
        public WeekResult result() {
            return WeekSummarizer.summarize(session.wordStates());
        }
    }

    /** Outcome of an answer plus what comes next. */
    public record AnswerOutcome(AnswerResult result, SessionView view) {
    }

    /**
     * Continues the open session of the week or starts a new one: the first pass while the week is
     * active and not yet passed, otherwise practice.
     */
    @Transactional
    public SessionView startOrResume(CurrentUser user, long weekId) {
        StudyWeek week = viewableWeek(user, weekId);
        Child child = childOf(week);
        lifecycle.refresh(child);
        // concurrent starts (a double tap, two tabs) wait here, so only one of them creates the session
        week = weeks.findByIdForUpdate(weekId).orElseThrow();
        if (week.getStatus().isPending()) {
            throw new AppException(Code.CONFLICT, "This week has not started yet");
        }
        Optional<SessionRecord> firstPass = sessions.findByWeekIdAndKind(weekId, SessionRecord.Kind.FIRST_PASS);
        if (week.getStatus() == WeekStatus.ACTIVE) {
            if (firstPass.isEmpty()) {
                return view(sessions.save(new SessionRecord(weekId, SessionRecord.Kind.FIRST_PASS,
                        random.nextLong(), clock.instant())), child);
            }
            if (firstPass.get().getStatus() == TrainingSession.Status.IN_PROGRESS) {
                return view(firstPass.get(), child);
            }
        }
        SessionRecord practice = sessions.findFirstByWeekIdAndKindAndStatusOrderByIdDesc(weekId,
                        SessionRecord.Kind.PRACTICE, TrainingSession.Status.IN_PROGRESS)
                .orElseGet(() -> sessions.save(new SessionRecord(weekId, SessionRecord.Kind.PRACTICE,
                        random.nextLong(), clock.instant())));
        return view(practice, child);
    }

    @Transactional
    public SessionView get(CurrentUser user, long sessionId) {
        SessionRecord record = viewableSession(user, sessionId);
        Child child = childOf(record);
        lifecycle.refresh(child);
        return view(sessions.findById(sessionId).orElseThrow(), child);
    }

    @Transactional
    public AnswerOutcome answer(CurrentUser user, long sessionId, long weekWordId, int round, String answer) {
        boolean repeat = events.existsBySessionIdAndKindAndWeekWordIdAndRoundNo(sessionId, SessionEvent.Kind.ANSWER,
                weekWordId, round);
        // a retried request for an answer already stored gets the stored result, even if the session has ended
        SessionRecord record = repeat ? refreshedSession(user, sessionId) : activeSession(user, sessionId);
        Child child = childOf(record);
        TrainingSession session = loader.load(record, child);
        AnswerResult result = engine(() -> session.submit(weekWordId, round, answer));
        if (!repeat && result.verdict() != AnswerResult.Verdict.IGNORED) {
            append(record, SessionEvent.Kind.ANSWER, weekWordId, round, answer.strip());
            record.sync(session, clock.instant());
            if (session.status() == TrainingSession.Status.COMPLETED) {
                if (record.getKind() == SessionRecord.Kind.FIRST_PASS) {
                    lifecycle.close(weeks.findById(record.getWeekId()).orElseThrow(), WeekStatus.COMPLETED);
                } else if (record.getKind() == SessionRecord.Kind.REVIEW) {
                    garden.applyReview(record, session, child);
                }
            }
        }
        return new AnswerOutcome(result, new SessionView(record, session));
    }

    @Transactional
    public SessionView hint(CurrentUser user, long sessionId, long weekWordId, int round) {
        SessionRecord record = activeSession(user, sessionId);
        Child child = childOf(record);
        TrainingSession session = loader.load(record, child);
        CurrentItem before = engine(() -> session.current().orElseThrow());
        engine(() -> session.hint(weekWordId, round));
        if (before.hintAvailable()) {
            append(record, SessionEvent.Kind.HINT, weekWordId, round, null);
        }
        return new SessionView(record, session);
    }

    // --- internals -------------------------------------------------------------------------------

    /** The session after bringing its week up to date (the week may just have expired). */
    private SessionRecord refreshedSession(CurrentUser user, long sessionId) {
        SessionRecord record = viewableSession(user, sessionId);
        lifecycle.refresh(childOf(record));
        return sessions.findById(sessionId).orElseThrow();
    }

    private SessionRecord activeSession(CurrentUser user, long sessionId) {
        SessionRecord record = refreshedSession(user, sessionId);
        if (record.getStatus() != TrainingSession.Status.IN_PROGRESS) {
            throw new AppException(Code.SESSION_CLOSED, "This training is " + record.getStatus().name().toLowerCase());
        }
        return record;
    }

    private void append(SessionRecord record, SessionEvent.Kind kind, long weekWordId, int round, String answer) {
        int seq = (int) events.countBySessionId(record.getId()) + 1;
        try {
            events.saveAndFlush(new SessionEvent(record.getId(), seq, kind, weekWordId, round, answer, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new AppException(Code.CONFLICT, "The training was changed at the same time; reload it");
        }
    }

    private <T> T engine(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (TrainingConflictException e) {
            throw new AppException(Code.CONFLICT, e.getMessage());
        } catch (SessionNotActiveException e) {
            throw new AppException(Code.SESSION_CLOSED, e.getMessage());
        }
    }

    private SessionView view(SessionRecord record, Child child) {
        return new SessionView(record, loader.load(record, child));
    }

    private SessionRecord viewableSession(CurrentUser user, long sessionId) {
        SessionRecord record = sessions.findById(sessionId).orElseThrow(() -> AppException.notFound("Training"));
        if (record.getKind() == SessionRecord.Kind.REVIEW) {
            Child child = childOf(record);
            if (!child.getParentId().equals(user.parentId()) || child.isArchived() || !user.canView(child.getId())) {
                throw AppException.notFound("Training");
            }
        } else {
            viewableWeek(user, record.getWeekId());
        }
        return record;
    }

    private StudyWeek viewableWeek(CurrentUser user, long weekId) {
        StudyWeek week = weeks.findById(weekId).orElseThrow(() -> AppException.notFound("Week"));
        Child child = children.findById(week.getChildId()).orElseThrow();
        if (!child.getParentId().equals(user.parentId()) || child.isArchived() || !user.canView(child.getId())) {
            throw AppException.notFound("Week");
        }
        return week;
    }

    private Child childOf(StudyWeek week) {
        return children.findById(week.getChildId()).orElseThrow();
    }

    private Child childOf(SessionRecord record) {
        long childId = record.getChildId() != null ? record.getChildId()
                : weeks.findById(record.getWeekId()).orElseThrow().getChildId();
        return children.findById(childId).orElseThrow();
    }
}
