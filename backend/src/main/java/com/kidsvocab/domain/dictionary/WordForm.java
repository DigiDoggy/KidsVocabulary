package com.kidsvocab.domain.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "word_form")
public class WordForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Column(nullable = false)
    private String lang;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private String normalized;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected WordForm() {
    }

    public WordForm(Long conceptId, String lang, String text, String normalized, boolean primary) {
        this.conceptId = conceptId;
        this.lang = lang;
        this.text = text;
        this.normalized = normalized;
        this.primary = primary;
    }

    public Long getConceptId() {
        return conceptId;
    }

    public String getLang() {
        return lang;
    }

    public String getText() {
        return text;
    }

    public String getNormalized() {
        return normalized;
    }

    public boolean isPrimary() {
        return primary;
    }
}
