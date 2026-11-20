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

    /** The first pass of a week scores points; practice runs do not. */
    public enum Kind { FIRST_PASS, PRACTICE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_id", nullable = false)
    private Long weekId;

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
