package com.kidsvocab.domain.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeekWordRepository extends JpaRepository<WeekWord, Long> {

    List<WeekWord> findByWeekIdOrderById(Long weekId);

    Optional<WeekWord> findByIdAndWeekId(Long id, Long weekId);

    long countByWeekId(Long weekId);
}
