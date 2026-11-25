package com.kidsvocab.domain.study;

import com.kidsvocab.core.training.TrainingSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** A stored training session; its state is rebuilt by replaying its {@link SessionEvent}s. */
@Entity
@Table(name = "training_session")
public class SessionRecord {

    /**
     * The first pass of a week scores points; practice runs do not. A review waters the child's word
     * garden: it belongs to the child, not to a week.
     */
    public enum Kind { FIRST_PASS, PRACTICE, REVIEW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code null} for a review. */
    @Column(name = "week_id")
    private Long weekId;

    /** Set for a review only. */
    @Column(name = "child_id")
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrainingSession.Status status;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    @Column(nullable = false)
    private long seed;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private int version;

    protected SessionRecord() {
    }

    public SessionRecord(Long weekId, Kind kind, long seed, Instant startedAt) {
        this.weekId = weekId;
        this.kind = kind;
        this.seed = seed;
        this.startedAt = startedAt;
        this.status = TrainingSession.Status.IN_PROGRESS;
        this.currentRound = 1;
    }

    /** A garden review of the child's words. */
    public static SessionRecord review(long childId, long seed, Instant startedAt) {
        SessionRecord r = new SessionRecord(null, Kind.REVIEW, seed, startedAt);
        r.childId = childId;
        return r;
    }

    /** Mirrors the engine state into the row (for queries; the event log stays the source of truth). */
    public void sync(TrainingSession session, Instant now) {
        this.currentRound = session.round();
        if (status != session.status() && session.status() == TrainingSession.Status.COMPLETED) {
            this.completedAt = now;
        }
        this.status = session.status();
    }

    /** Stops an unfinished session, e.g. when its week ends. */
    public void closeIfOpen() {
        if (status == TrainingSession.Status.IN_PROGRESS) {
            status = TrainingSession.Status.CLOSED;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getWeekId() {
        return weekId;
    }

    public Long getChildId() {
        return childId;
    }

    public Kind getKind() {
        return kind;
    }

    public TrainingSession.Status getStatus() {
        return status;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public long getSeed() {
        return seed;
    }
}
