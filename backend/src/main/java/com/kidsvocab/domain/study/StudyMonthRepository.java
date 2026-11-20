package com.kidsvocab.domain.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyMonthRepository extends JpaRepository<StudyMonth, Long> {

    Optional<StudyMonth> findByChildIdAndStatus(Long childId, StudyMonth.Status status);

    List<StudyMonth> findByChildIdOrderBySeqNoDesc(Long childId);
}
