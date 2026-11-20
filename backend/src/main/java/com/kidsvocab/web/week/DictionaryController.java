package com.kidsvocab.web.week;

import com.kidsvocab.domain.dictionary.ConceptRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dictionary")
public class DictionaryController {

    private final ConceptRepository concepts;

    public DictionaryController(ConceptRepository concepts) {
        this.concepts = concepts;
    }

    /** Topics available for suggestions. */
    @GetMapping("/topics")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public List<String> topics() {
        return concepts.findSharedTopics();
    }
}
