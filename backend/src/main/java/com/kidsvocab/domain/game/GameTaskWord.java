package com.kidsvocab.domain.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A word of a game task, with its texts copied from the dictionary. */
@Entity
@Table(name = "game_task_word")
public class GameTaskWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Column(name = "prompt_text", nullable = false)
    private String promptText;

    @Column(name = "answer_text", nullable = false)
    private String answerText;

    protected GameTaskWord() {
    }

    public GameTaskWord(Long taskId, Long conceptId, String promptText, String answerText) {
        this.taskId = taskId;
        this.conceptId = conceptId;
        this.promptText = promptText;
        this.answerText = answerText;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getConceptId() {
        return conceptId;
    }

    public String getPromptText() {
        return promptText;
    }

    public String getAnswerText() {
        return answerText;
    }
}
