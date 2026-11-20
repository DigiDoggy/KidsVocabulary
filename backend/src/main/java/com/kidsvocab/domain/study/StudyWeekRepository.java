package com.kidsvocab.domain.study;

import org.springframework.data.jpa.repository.JpaRepository;

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

    Optional<StudyWeek> findFirstByChildIdAndStatusIn(Long childId, Collection<WeekStatus> statuses);
}
