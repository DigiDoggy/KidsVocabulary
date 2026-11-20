package com.kidsvocab.domain.study;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One step of a training session: an answer or a hint request. */
@Entity
@Table(name = "training_event")
public class SessionEvent {

    public enum Kind { ANSWER, HINT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "week_word_id", nullable = false)
    private Long weekWordId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "answer_text")
    private String answerText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SessionEvent() {
    }

    public SessionEvent(Long sessionId, int seq, Kind kind, Long weekWordId, int roundNo, String answerText,
                        Instant createdAt) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.kind = kind;
        this.weekWordId = weekWordId;
        this.roundNo = roundNo;
        this.answerText = answerText;
        this.createdAt = createdAt;
    }

    public int getSeq() {
        return seq;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getWeekWordId() {
        return weekWordId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public String getAnswerText() {
        return answerText;
    }
}
