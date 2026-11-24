package com.kidsvocab.web.training;

import com.kidsvocab.app.training.TrainingService;
import com.kidsvocab.app.training.TrainingService.AnswerOutcome;
import com.kidsvocab.app.training.TrainingService.SessionView;
import com.kidsvocab.core.scoring.ScoringPolicy;
import com.kidsvocab.core.training.AnswerResult;
import com.kidsvocab.core.training.CurrentItem;
import com.kidsvocab.core.training.TrainingSession;
import com.kidsvocab.core.week.WordResult;
import com.kidsvocab.domain.study.SessionRecord;
import com.kidsvocab.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Training for the child (and for the parent, who may try it too). */
@RestController
@RequestMapping("/api/v1")
public class TrainingController {

    private final TrainingService training;
    private final int showCorrectAnswerSeconds;

    public TrainingController(TrainingService training,
                              @Value("${app.training.show-correct-answer-seconds}") int showCorrectAnswerSeconds) {
        this.training = training;
        this.showCorrectAnswerSeconds = showCorrectAnswerSeconds;
    }

    public record AnswerRequest(@NotNull Long weekWordId, @NotNull @Min(1) Integer round,
                                @Size(max = 100) String answer) {
    }

    public record HintRequest(@NotNull Long weekWordId, @NotNull @Min(1) Integer round) {
    }

    /**
     * @param points    points as they stand (first pass: what counts; practice: for fun only)
     * @param counts    whether this session's points count towards the month
     */
    public record SessionResponse(long id, long weekId, SessionRecord.Kind kind, TrainingSession.Status status,
                                  int round, ItemView current, BigDecimal points, BigDecimal maxPoints,
                                  boolean counts, List<WordStateView> words) {

        static SessionResponse of(SessionView v, Map<Long, String> emojis) {
            var result = v.result();
            return new SessionResponse(v.record().getId(), v.record().getWeekId(), v.record().getKind(),
                    v.session().status(), v.session().round(), ItemView.of(v.current(), emojis), result.points(),
                    result.maxPoints(), v.record().getKind() == SessionRecord.Kind.FIRST_PASS,
                    result.words().stream().map(WordStateView::of).toList());
        }
    }

    /** The current word as the client sees it: {@link CurrentItem} plus its picture ({@code null} if none). */
    public record ItemView(long wordId, int round, int position, String prompt, String emoji, String hintMask,
                           boolean hintAvailable, int roundSize, int remainingInRound) {

        static ItemView of(Optional<CurrentItem> current, Map<Long, String> emojis) {
            return current.map(c -> new ItemView(c.wordId(), c.round(), c.position(), c.prompt(),
                    emojis.get(c.wordId()), c.hintMask(), c.hintAvailable(), c.roundSize(), c.remainingInRound()))
                    .orElse(null);
        }
    }

    public record WordStateView(long weekWordId, Integer solvedRound, boolean hintUsed, boolean problematic,
                                BigDecimal points) {

        static WordStateView of(WordResult r) {
            return new WordStateView(r.wordId(), r.solvedRound(), r.hintUsed(), r.problematic(),
                    r.solvedRound() == null ? ScoringPolicy.ZERO : r.points());
        }
    }

    /**
     * @param correctAnswer          the right spelling (to show after a mistake)
     * @param showCorrectAnswerFor   seconds the right spelling stays on screen after a mistake
     * @param next                   the next word, or {@code null} when the session is finished
     */
    public record AnswerResponse(AnswerResult.Verdict verdict, String correctAnswer, int showCorrectAnswerFor,
                                 boolean becameProblematic, boolean roundFinished, boolean sessionFinished,
                                 ItemView next, BigDecimal points) {
    }

    public record HintResponse(String hintMask, boolean hintAvailable) {
    }

    @PostMapping("/weeks/{weekId}/sessions")
    public SessionResponse start(CurrentUser user, @PathVariable long weekId) {
        return respond(training.startOrResume(user, weekId));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionResponse get(CurrentUser user, @PathVariable long sessionId) {
        return respond(training.get(user, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/answers")
    public AnswerResponse answer(CurrentUser user, @PathVariable long sessionId,
                                 @Valid @RequestBody AnswerRequest request) {
        AnswerOutcome outcome = training.answer(user, sessionId, request.weekWordId(), request.round(),
                request.answer());
        AnswerResult r = outcome.result();
        return new AnswerResponse(r.verdict(), r.correctAnswer(), showCorrectAnswerSeconds, r.becameProblematic(),
                r.roundFinished(), r.sessionFinished(),
                ItemView.of(outcome.view().current(), training.emojis(outcome.view().record().getWeekId())),
                outcome.view().result().points());
    }

    private SessionResponse respond(SessionView view) {
        return SessionResponse.of(view, training.emojis(view.record().getWeekId()));
    }

    @PostMapping("/sessions/{sessionId}/hint")
    public HintResponse hint(CurrentUser user, @PathVariable long sessionId, @Valid @RequestBody HintRequest request) {
        CurrentItem item = training.hint(user, sessionId, request.weekWordId(), request.round()).current()
                .orElseThrow();
        return new HintResponse(item.hintMask(), item.hintAvailable());
    }
}
