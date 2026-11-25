package com.kidsvocab.domain.study;

import com.kidsvocab.core.training.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRecordRepository extends JpaRepository<SessionRecord, Long> {

    Optional<SessionRecord> findByWeekIdAndKind(Long weekId, SessionRecord.Kind kind);

    Optional<SessionRecord> findFirstByWeekIdAndKindAndStatusOrderByIdDesc(Long weekId, SessionRecord.Kind kind,
                                                                          TrainingSession.Status status);

    Optional<SessionRecord> findFirstByChildIdAndKindAndStatusOrderByIdDesc(Long childId, SessionRecord.Kind kind,
                                                                           TrainingSession.Status status);
}
