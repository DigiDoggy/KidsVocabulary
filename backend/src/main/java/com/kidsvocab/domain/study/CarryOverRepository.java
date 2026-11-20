package com.kidsvocab.domain.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CarryOverRepository extends JpaRepository<CarryOver, Long> {

    /** Words still waiting for a week, oldest first. */
    List<CarryOver> findByChildIdAndPlacedWeekIdIsNullOrderById(Long childId);

    Optional<CarryOver> findByChildIdAndConceptIdAndPlacedWeekId(Long childId, Long conceptId, Long placedWeekId);

    boolean existsByChildIdAndConceptIdAndFromMonthId(Long childId, Long conceptId, Long fromMonthId);
}
