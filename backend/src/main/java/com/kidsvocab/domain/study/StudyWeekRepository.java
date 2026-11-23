package com.kidsvocab.domain.study;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyWeekRepository extends JpaRepository<StudyWeek, Long> {

    List<StudyWeek> findByChildIdOrderByStartDateDescIdDesc(Long childId);

    List<StudyWeek> findByChildIdAndStatusIn(Long childId, Collection<WeekStatus> statuses);

    List<StudyWeek> findByMonthIdOrderByWeekIndex(Long monthId);

    boolean existsByChildIdAndStartDateAndStatusNot(Long childId, LocalDate startDate, WeekStatus status);

    boolean existsByChildIdAndStatusIn(Long childId, Collection<WeekStatus> statuses);

    long countByMonthId(Long monthId);

    /** True once the child has had at least one running week. */
    boolean existsByChildIdAndMonthIdIsNotNull(Long childId);

    Optional<StudyWeek> findFirstByChildIdAndStatusIn(Long childId, Collection<WeekStatus> statuses);

    /** The week with its row locked until the transaction ends; serializes work that must happen once per week. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from StudyWeek w where w.id = :id")
    Optional<StudyWeek> findByIdForUpdate(Long id);
}
