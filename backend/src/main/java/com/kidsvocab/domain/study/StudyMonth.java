package com.kidsvocab.domain.study;

import com.kidsvocab.core.month.MonthReport;
import com.kidsvocab.core.month.MonthResult;
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

import java.math.BigDecimal;
import java.time.Instant;

/** A cycle of four study weeks. */
@Entity
@Table(name = "study_month")
public class StudyMonth {

    public enum Status { ACTIVE, COMPLETED }

    public static final int WEEKS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "reward_text")
    private String rewardText;

    @Column(name = "total_points")
    private BigDecimal totalPoints;

    @Column(name = "max_points")
    private BigDecimal maxPoints;

    private BigDecimal percent;

    @Enumerated(EnumType.STRING)
    private MonthResult result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private MonthReport report;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected StudyMonth() {
    }

    public StudyMonth(Long childId, int seqNo) {
        this.childId = childId;
        this.seqNo = seqNo;
        this.status = Status.ACTIVE;
    }

    public void complete(MonthReport report, Instant now) {
        this.report = report;
        this.totalPoints = report.totalPoints();
        this.maxPoints = report.maxPoints();
        this.percent = report.percent();
        this.result = report.result();
        this.status = Status.COMPLETED;
        this.completedAt = now;
    }

    public void changeReward(String rewardText) {
        this.rewardText = rewardText;
    }

    public Long getId() {
        return id;
    }

    public Long getChildId() {
        return childId;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public Status getStatus() {
        return status;
    }

    public String getRewardText() {
        return rewardText;
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public MonthResult getResult() {
        return result;
    }

    public MonthReport getReport() {
        return report;
    }
}
