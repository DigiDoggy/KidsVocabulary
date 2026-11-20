package com.kidsvocab.domain.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConceptRepository extends JpaRepository<Concept, Long> {

    @Query("select distinct c.topic from Concept c where c.ownerParentId is null and c.topic is not null order by c.topic")
    List<String> findSharedTopics();
}
