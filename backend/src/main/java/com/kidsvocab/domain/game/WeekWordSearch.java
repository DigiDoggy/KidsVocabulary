package com.kidsvocab.domain.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The word-search grid of a week, kept so the child gets the same grid every time. */
@Entity
@Table(name = "week_word_search")
public class WeekWordSearch {

    @Id
    @Column(name = "week_id")
    private Long weekId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private WordSearchGrid grid;

    protected WeekWordSearch() {
    }

    public WeekWordSearch(Long weekId, WordSearchGrid grid) {
        this.weekId = weekId;
        this.grid = grid;
    }

    public Long getWeekId() {
        return weekId;
    }

    public WordSearchGrid getGrid() {
        return grid;
    }
}
