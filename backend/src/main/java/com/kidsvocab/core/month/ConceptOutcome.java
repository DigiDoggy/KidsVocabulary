package com.kidsvocab.core.month;

import com.kidsvocab.core.scoring.Outcome;

/** Outcome of a dictionary concept in one week (the concept, not the week word, is carried over). */
public record ConceptOutcome(long conceptId, Outcome outcome) {
}
