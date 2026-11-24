package com.kidsvocab.web.training;

import com.kidsvocab.app.training.ProgressService;
import com.kidsvocab.app.training.ProgressService.Home;
import com.kidsvocab.app.training.ProgressService.MonthProgress;
import com.kidsvocab.app.training.ProgressService.Report;
import com.kidsvocab.app.training.ProgressService.ReportWord;
import com.kidsvocab.core.month.MonthResult;
import com.kidsvocab.core.month.WeekStats;
import com.kidsvocab.domain.child.Gender;
import com.kidsvocab.domain.study.StudyMonth;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.WeekStatus;
import com.kidsvocab.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProgressController {

    private final ProgressService progress;

    public ProgressController(ProgressService progress) {
        this.progress = progress;
    }

    // --- DTOs ------------------------------------------------------------------------------------

    public record ChildInfo(long id, String name, Gender gender, String nativeLang, String targetLang) {
    }

    public record WeekInfo(long id, WeekStatus status, LocalDate startDate, LocalDate endDate, Integer weekIndex,
                           int wordCount, BigDecimal points, BigDecimal maxPoints, boolean firstPassOpen) {
    }

    public record MonthInfo(long id, int seqNo, StudyMonth.Status status, int weeksClosed, BigDecimal points,
                            BigDecimal maxPoints, String reward) {
    }

    public record HomeResponse(ChildInfo child, WeekInfo week, MonthInfo month) {

        static HomeResponse of(Home h) {
            ChildInfo child = new ChildInfo(h.child().getId(), h.child().getName(), h.child().getGender(),
                    h.child().getNativeLang(), h.child().getTargetLang());
            StudyWeek w = h.week();
            WeekInfo week = w == null ? null : new WeekInfo(w.getId(), w.getStatus(), w.getStartDate(), w.getEndDate(),
                    w.getWeekIndex(), w.getWordCount(), h.weekPoints(), h.weekMaxPoints(), h.firstPassOpen());
            StudyMonth m = h.month();
            MonthInfo month = m == null ? null : new MonthInfo(m.getId(), m.getSeqNo(), m.getStatus(), h.weeksClosed(),
                    h.monthPoints(), h.monthMaxPoints(), m.getRewardText());
            return new HomeResponse(child, week, month);
        }
    }

    public record MonthSummary(long id, int seqNo, StudyMonth.Status status, String reward, BigDecimal percent,
                               MonthResult result) {

        static MonthSummary of(StudyMonth m) {
            return new MonthSummary(m.getId(), m.getSeqNo(), m.getStatus(), m.getRewardText(), m.getPercent(),
                    m.getResult());
        }
    }

    public record MonthWeekRow(long id, Integer weekIndex, LocalDate startDate, WeekStatus status, int wordCount,
                               BigDecimal points, BigDecimal maxPoints) {
    }

    public record MonthResponse(long id, int seqNo, StudyMonth.Status status, String reward, BigDecimal points,
                                BigDecimal maxPoints, BigDecimal percent, MonthResult result,
                                List<MonthWeekRow> weeks) {

        static MonthResponse of(MonthProgress p) {
            StudyMonth m = p.month();
            return new MonthResponse(m.getId(), m.getSeqNo(), m.getStatus(), m.getRewardText(), p.points(),
                    p.maxPoints(), m.getPercent(), m.getResult(),
                    p.weeks().stream().map(w -> new MonthWeekRow(w.getId(), w.getWeekIndex(), w.getStartDate(),
                            w.getStatus(), w.getWordCount(), w.getPoints(), w.getMaxPoints())).toList());
        }
    }

    /**
     * @param reward the agreed reward, present only when the result is {@code REWARD}
     */
    public record ReportResponse(long monthId, int seqNo, BigDecimal totalPoints, BigDecimal maxPoints,
                                 BigDecimal percent, MonthResult result, String reward, List<WeekStats> weeks,
                                 List<ReportWord> hardWords, List<ReportWord> unlearnedWords) {

        static ReportResponse of(Report r) {
            var report = r.report();
            return new ReportResponse(r.month().getId(), r.month().getSeqNo(), report.totalPoints(),
                    report.maxPoints(), report.percent(), report.result(),
                    report.result() == MonthResult.REWARD ? r.month().getRewardText() : null,
                    report.weeks(), r.hardWords(), r.unlearnedWords());
        }
    }

    public record RewardRequest(@Size(max = 255) String reward) {
    }

    public record WordStatResponse(long weekWordId, String prompt, String answer, int wrongRounds,
                                   Integer solvedRound, boolean hintUsed, boolean problematic, BigDecimal points,
                                   String outcome) {
    }

    // --- endpoints -------------------------------------------------------------------------------

    /** Home screen of the open child profile. */
    @GetMapping("/me/home")
    @PreAuthorize("hasAuthority('MODE_CHILD')")
    public HomeResponse myHome(CurrentUser user) {
        return HomeResponse.of(progress.home(user, null));
    }

    /** The same view for the parent. */
    @GetMapping("/children/{childId}/home")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public HomeResponse childHome(CurrentUser user, @PathVariable long childId) {
        return HomeResponse.of(progress.home(user, childId));
    }

    @GetMapping("/children/{childId}/months")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public List<MonthSummary> months(CurrentUser user, @PathVariable long childId) {
        return progress.months(user, childId).stream().map(MonthSummary::of).toList();
    }

    @PutMapping("/children/{childId}/months/current/reward")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public MonthSummary setReward(CurrentUser user, @PathVariable long childId,
                                  @Valid @RequestBody RewardRequest request) {
        return MonthSummary.of(progress.setReward(user, childId, request.reward()));
    }

    @GetMapping("/months/{monthId}")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public MonthResponse month(CurrentUser user, @PathVariable long monthId) {
        return MonthResponse.of(progress.month(user, monthId));
    }

    @GetMapping("/months/{monthId}/report")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public ReportResponse report(CurrentUser user, @PathVariable long monthId) {
        return ReportResponse.of(progress.report(user, monthId));
    }

    @GetMapping("/weeks/{weekId}/stats")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public List<WordStatResponse> weekStats(CurrentUser user, @PathVariable long weekId) {
        return progress.weekStats(user, weekId).stream()
                .map(s -> new WordStatResponse(s.word().getId(), s.word().getPromptText(), s.word().getPrimaryAnswer(),
                        s.wrongRounds(), s.solvedRound(), s.hintUsed(), s.problematic(), s.points(), s.outcome()))
                .toList();
    }
}
