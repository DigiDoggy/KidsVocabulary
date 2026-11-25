package com.kidsvocab.app.garden;

import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.app.week.WeekLifecycleService;
import com.kidsvocab.core.garden.Plant;
import com.kidsvocab.core.training.TrainingSession;
import com.kidsvocab.core.training.WordState;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.study.GardenRepository;
import com.kidsvocab.domain.study.GardenRepository.GardenPlant;
import com.kidsvocab.domain.study.SessionRecord;
import com.kidsvocab.domain.study.SessionRecordRepository;
import com.kidsvocab.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The child's word garden: one plant per learned word, watered by review sessions. Reviews are
 * trained like practice and never change week points, the monthly result or the word history.
 */
@Service
public class GardenService {

    private final GardenRepository garden;
    private final SessionRecordRepository sessions;
    private final ChildRepository children;
    private final WeekLifecycleService lifecycle;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GardenService(GardenRepository garden, SessionRecordRepository sessions, ChildRepository children,
                         WeekLifecycleService lifecycle, JdbcTemplate jdbc, Clock clock) {
        this.garden = garden;
        this.sessions = sessions;
        this.children = children;
        this.lifecycle = lifecycle;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param reviewSize   how many words one watering takes at most (the child's week size)
     * @param openReviewId an unfinished watering to continue, if any
     */
    public record GardenView(LocalDate today, List<GardenPlant> plants, int dueCount, int reviewSize,
                             Long openReviewId) {
    }

    @Transactional
    public GardenView garden(CurrentUser user) {
        Child child = childOf(user);
        lifecycle.refresh(child);
        LocalDate today = lifecycle.today(child);
        return new GardenView(today, garden.plants(child.getId()), garden.countDue(child.getId(), today),
                child.getDefaultWeekSize(), openReview(child.getId()).map(SessionRecord::getId).orElse(null));
    }

    /** Continues the unfinished watering or starts one with the due plants, the most overdue first. */
    @Transactional
    public SessionRecord startOrResumeReview(CurrentUser user) {
        Child child = childOf(user);
        lifecycle.refresh(child);
        // concurrent starts (a double tap, two tabs) wait here, so only one of them creates the session
        jdbc.queryForObject("select id from child where id = ? for update", Long.class, child.getId());
        Optional<SessionRecord> open = openReview(child.getId());
        if (open.isPresent()) {
            return open.get();
        }
        List<GardenPlant> due = garden.due(child.getId(), lifecycle.today(child), child.getDefaultWeekSize());
        if (due.isEmpty()) {
            throw new AppException(Code.CONFLICT, "No plant needs water today");
        }
        SessionRecord record = sessions.saveAndFlush(SessionRecord.review(child.getId(), random.nextLong(),
                clock.instant()));
        garden.addReviewWords(record.getId(), due);
        return record;
    }

    /** Grows (or re-schedules) every plant of a finished review. */
    @Transactional
    public void applyReview(SessionRecord record, TrainingSession session, Child child) {
        Map<Long, Long> conceptByWord = garden.reviewWords(record.getId());
        Map<Long, GardenPlant> plants = garden.plantsOf(child.getId(), new ArrayList<>(conceptByWord.values()))
                .stream().collect(Collectors.toMap(GardenPlant::conceptId, Function.identity()));
        LocalDate today = lifecycle.today(child);
        for (WordState state : session.wordStates()) {
            Long conceptId = conceptByWord.get(state.wordId());
            GardenPlant plant = conceptId == null ? null : plants.get(conceptId);
            if (plant != null) {
                garden.update(child.getId(), conceptId, plant.plant().reviewed(Plant.succeeded(state), today));
            }
        }
    }

    private Optional<SessionRecord> openReview(long childId) {
        return sessions.findFirstByChildIdAndKindAndStatusOrderByIdDesc(childId, SessionRecord.Kind.REVIEW,
                TrainingSession.Status.IN_PROGRESS);
    }

    private Child childOf(CurrentUser user) {
        if (user.isParent()) {
            throw new AppException(Code.FORBIDDEN, "Child mode required");
        }
        return children.findById(user.childId()).filter(c -> !c.isArchived())
                .orElseThrow(() -> AppException.notFound("Child"));
    }
}
