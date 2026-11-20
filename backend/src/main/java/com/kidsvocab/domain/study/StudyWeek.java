package com.kidsvocab.domain.study;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "study_week")
public class StudyWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_id")
    private Long monthId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "week_index")
    private Integer weekIndex;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WeekStatus status;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "launched_at")
    private Instant launchedAt;

    @Column(name = "first_pass_completed_at")
    private Instant firstPassCompletedAt;

    private BigDecimal points;

    @Column(name = "max_points")
    private BigDecimal maxPoints;

    protected StudyWeek() {
    }

    public StudyWeek(Long childId, LocalDate startDate, int wordCount) {
        this.childId = childId;
        this.status = WeekStatus.DRAFT;
        reschedule(startDate);
        this.wordCount = wordCount;
    }

    public void reschedule(LocalDate monday) {
        this.startDate = monday;
        this.endDate = monday.plusDays(6);
    }

    public void changeWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public void schedule(Instant now) {
        status = WeekStatus.SCHEDULED;
        launchedAt = now;
    }

    public void unschedule() {
        status = WeekStatus.DRAFT;
        launchedAt = null;
    }

    public void activate(Long monthId, int weekIndex, Instant now) {
        this.monthId = monthId;
        this.weekIndex = weekIndex;
        this.status = WeekStatus.ACTIVE;
        if (launchedAt == null) {
            launchedAt = now;
        }
    }

    public void close(WeekStatus closedStatus, BigDecimal points, BigDecimal maxPoints, Instant now) {
        this.status = closedStatus;
        this.points = points;
        this.maxPoints = maxPoints;
        if (closedStatus == WeekStatus.COMPLETED) {
            this.firstPassCompletedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getMonthId() {
        return monthId;
    }

    public Long getChildId() {
        return childId;
    }

    public Integer getWeekIndex() {
        return weekIndex;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public WeekStatus getStatus() {
        return status;
    }

    public int getWordCount() {
        return wordCount;
    }

    public Instant getLaunchedAt() {
        return launchedAt;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }
}
