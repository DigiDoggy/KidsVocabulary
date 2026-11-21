package com.kidsvocab.web.week;

import com.kidsvocab.app.week.WeekService.WeekDetails;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.WeekStatus;
import com.kidsvocab.domain.study.WeekWord;
import com.kidsvocab.domain.study.WordSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WeekDtos {

    private WeekDtos() {
    }

    public record CreateWeekRequest(LocalDate startDate, @Min(1) @Max(50) Integer wordCount) {
    }

    public record UpdateWeekRequest(LocalDate startDate, @NotNull @Min(1) @Max(50) Integer wordCount) {
    }

    public record AddWordRequest(
            @NotBlank @Size(max = 60) String source,
            @NotBlank @Size(max = 60) String target,
            @Size(max = 5) List<@Size(max = 60) String> extraAnswers) {
    }

    /**
     * A dictionary topic or the parent's own one, which goes into the AI prompt as typed:
     * letters, spaces, hyphens and apostrophes only.
     */
    public record TopicRequest(@Size(max = 40) @Pattern(regexp = "[\\p{L} '-]*") String topic) {
    }

    /** Summary row for week lists. */
    public record WeekSummary(long id, LocalDate startDate, LocalDate endDate, WeekStatus status, Integer weekIndex,
                              int wordCount, BigDecimal points, BigDecimal maxPoints) {

        static WeekSummary of(StudyWeek w) {
            return new WeekSummary(w.getId(), w.getStartDate(), w.getEndDate(), w.getStatus(), w.getWeekIndex(),
                    w.getWordCount(), w.getPoints(), w.getMaxPoints());
        }
    }

    /**
     * @param answer       primary spelling
     * @param extraAnswers other accepted spellings (normalised)
     */
    public record WordView(long id, long conceptId, WordSource source, String prompt, String answer,
                           List<String> extraAnswers, BigDecimal points, String outcome) {

        static WordView of(WeekWord w) {
            List<String> accepted = w.getAcceptedAnswers();
            return new WordView(w.getId(), w.getConceptId(), w.getSource(), w.getPromptText(), w.getPrimaryAnswer(),
                    accepted.subList(1, accepted.size()), w.getPoints(),
                    w.getOutcome() == null ? null : w.getOutcome().name());
        }
    }

    public record WeekResponse(long id, long childId, LocalDate startDate, LocalDate endDate, WeekStatus status,
                               Integer weekIndex, int wordCount, int missing, BigDecimal points,
                               BigDecimal maxPoints, List<WordView> words) {

        static WeekResponse of(WeekDetails d) {
            StudyWeek w = d.week();
            return new WeekResponse(w.getId(), w.getChildId(), w.getStartDate(), w.getEndDate(), w.getStatus(),
                    w.getWeekIndex(), w.getWordCount(), d.missing(), w.getPoints(), w.getMaxPoints(),
                    d.words().stream().map(WordView::of).toList());
        }
    }
}
