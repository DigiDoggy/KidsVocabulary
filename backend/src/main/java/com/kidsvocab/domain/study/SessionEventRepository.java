package com.kidsvocab.domain.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {

    List<SessionEvent> findBySessionIdOrderBySeq(Long sessionId);

    long countBySessionId(Long sessionId);

    boolean existsBySessionIdAndKindAndWeekWordIdAndRoundNo(Long sessionId, SessionEvent.Kind kind, Long weekWordId,
                                                           int roundNo);
}
