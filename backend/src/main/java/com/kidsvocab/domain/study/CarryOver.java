package com.kidsvocab.domain.study;

import com.kidsvocab.core.month.CarryOverPlanner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A hard or unlearned word waiting to be placed into a week of a later month. */
@Entity
@Table(name = "carry_over")
public class CarryOver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Column(name = "from_month_id", nullable = false)
    private Long fromMonthId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CarryOverPlanner.Reason reason;

    @Column(name = "placed_week_id")
    private Long placedWeekId;

    protected CarryOver() {
    }

    public CarryOver(Long childId, Long conceptId, Long fromMonthId, CarryOverPlanner.Reason reason) {
        this.childId = childId;
        this.conceptId = conceptId;
        this.fromMonthId = fromMonthId;
        this.reason = reason;
    }

    public void placeInto(Long weekId) {
        this.placedWeekId = weekId;
    }

    public Long getId() {
        return id;
    }

    public Long getConceptId() {
        return conceptId;
    }

    public CarryOverPlanner.Reason getReason() {
        return reason;
    }

    public Long getPlacedWeekId() {
        return placedWeekId;
    }
}
