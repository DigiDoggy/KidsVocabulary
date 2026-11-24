package com.kidsvocab.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameTaskRepository extends JpaRepository<GameTask, Long> {

    List<GameTask> findByChildIdOrderByIdDesc(Long childId);

    List<GameTask> findByChildIdAndStatusOrderByIdDesc(Long childId, GameTask.Status status);

    Optional<GameTask> findByIdAndChildId(Long id, Long childId);
}
