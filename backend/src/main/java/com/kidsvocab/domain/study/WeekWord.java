package com.kidsvocab.domain.study;

import com.kidsvocab.core.scoring.Outcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;

/**
 * A word of a weekly set. Prompt and accepted answers are copied from the dictionary when the word
 * is added, so later dictionary changes never alter a running week.
 */
@Entity
@Table(name = "week_word")
public class WeekWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_id", nullable = false)
    private Long weekId;

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WordSource source;

    @Column(name = "prompt_text", nullable = false)
    private String promptText;

    @Column(name = "primary_answer", nullable = false)
    private String primaryAnswer;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "accepted_answers", nullable = false, columnDefinition = "text[]")
    private List<String> acceptedAnswers;

    @Column(name = "attempt_no")
    private Integer attemptNo;

    @Column(name = "hint_used")
    private Boolean hintUsed;

    private Boolean problematic;

    private BigDecimal points;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    protected WeekWord() {
    }

    public WeekWord(Long weekId, Long conceptId, WordSource source, String promptText, String primaryAnswer,
                    List<String> acceptedAnswers) {
        this.weekId = weekId;
        this.conceptId = conceptId;
        this.source = source;
        this.promptText = promptText;
        this.primaryAnswer = primaryAnswer;
        this.acceptedAnswers = List.copyOf(acceptedAnswers);
    }

    public void recordResult(Integer attemptNo, boolean hintUsed, boolean problematic, BigDecimal points,
                             Outcome outcome) {
        this.attemptNo = attemptNo;
        this.hintUsed = hintUsed;
        this.problematic = problematic;
        this.points = points;
        this.outcome = outcome;
    }

    public Long getId() {
        return id;
    }

    public Long getWeekId() {
        return weekId;
    }

    public Long getConceptId() {
        return conceptId;
    }

    public WordSource getSource() {
        return source;
    }

    public String getPromptText() {
        return promptText;
    }

    public String getPrimaryAnswer() {
        return primaryAnswer;
    }

    public List<String> getAcceptedAnswers() {
        return acceptedAnswers;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public Outcome getOutcome() {
        return outcome;
    }
}
