package com.kidsvocab.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameTaskWordRepository extends JpaRepository<GameTaskWord, Long> {

    List<GameTaskWord> findByTaskIdOrderById(Long taskId);

    Optional<GameTaskWord> findByIdAndTaskId(Long id, Long taskId);

    long countByTaskId(Long taskId);
}
