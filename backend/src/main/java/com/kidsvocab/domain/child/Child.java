package com.kidsvocab.domain.child;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "child")
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    @Column(name = "native_lang", nullable = false)
    private String nativeLang;

    @Column(name = "target_lang", nullable = false)
    private String targetLang;

    @Column(name = "default_week_size", nullable = false)
    private int defaultWeekSize;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Child() {
    }

    public Child(Long parentId, Instant createdAt) {
        this.parentId = parentId;
        this.createdAt = createdAt;
    }

    public void update(String name, LocalDate birthDate, Level level, String nativeLang, String targetLang,
                       int defaultWeekSize) {
        this.name = name;
        this.birthDate = birthDate;
        this.level = level;
        this.nativeLang = nativeLang;
        this.targetLang = targetLang;
        this.defaultWeekSize = defaultWeekSize;
    }

    public void archive() {
        archived = true;
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Level getLevel() {
        return level;
    }

    public String getNativeLang() {
        return nativeLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public int getDefaultWeekSize() {
        return defaultWeekSize;
    }

    public boolean isArchived() {
        return archived;
    }
}
