package com.kidsvocab.domain.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A word meaning, independent of language; its spellings are {@link WordForm}s. */
@Entity
@Table(name = "concept")
public class Concept {

    public enum Origin { SEED, AI, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;

    @Column(nullable = false)
    private int difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origin origin;

    /** Null for the shared dictionary; set for words a parent typed in. */
    @Column(name = "owner_parent_id")
    private Long ownerParentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** One emoji that pictures the word, or {@code null}. */
    private String emoji;

    protected Concept() {
    }

    /** Difficulty by length of the target word: 1 (up to 4 characters), 2 (up to 7), 3 (longer). */
    public static int difficultyOf(String target) {
        int length = target.strip().length();
        return length <= 4 ? 1 : length <= 7 ? 2 : 3;
    }

    public Concept(String topic, int difficulty, Origin origin, Long ownerParentId, Instant createdAt) {
        this.topic = topic;
        this.difficulty = difficulty;
        this.origin = origin;
        this.ownerParentId = ownerParentId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public Origin getOrigin() {
        return origin;
    }

    public Long getOwnerParentId() {
        return ownerParentId;
    }

    public String getEmoji() {
        return emoji;
    }

    public void changeEmoji(String emoji) {
        this.emoji = emoji;
    }
}
