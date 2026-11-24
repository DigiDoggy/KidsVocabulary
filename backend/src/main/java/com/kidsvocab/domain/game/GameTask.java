package com.kidsvocab.domain.game;

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

import java.time.Instant;

/** A game the parent gives the child: a mode and a word list. Unscored, it can be played any number of times. */
@Entity
@Table(name = "game_task")
public class GameTask {

    public enum Status { DRAFT, GIVEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "given_at")
    private Instant givenAt;

    @Column(name = "times_played", nullable = false)
    private int timesPlayed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private WordSearchGrid grid;

    protected GameTask() {
    }

    public GameTask(Long childId, GameMode mode, Instant createdAt) {
        this.childId = childId;
        this.mode = mode;
        this.status = Status.DRAFT;
        this.createdAt = createdAt;
    }

    public void give(WordSearchGrid grid, Instant now) {
        this.grid = grid;
        this.status = Status.GIVEN;
        this.givenAt = now;
    }

    public void played() {
        timesPlayed++;
    }

    public Long getId() {
        return id;
    }

    public Long getChildId() {
        return childId;
    }

    public GameMode getMode() {
        return mode;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getGivenAt() {
        return givenAt;
    }

    public int getTimesPlayed() {
        return timesPlayed;
    }

    public WordSearchGrid getGrid() {
        return grid;
    }
}
