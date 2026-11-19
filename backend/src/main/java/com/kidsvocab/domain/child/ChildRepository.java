package com.kidsvocab.domain.child;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChildRepository extends JpaRepository<Child, Long> {

    List<Child> findByParentIdAndArchivedFalseOrderById(Long parentId);

    Optional<Child> findByIdAndParentIdAndArchivedFalse(Long id, Long parentId);
}
