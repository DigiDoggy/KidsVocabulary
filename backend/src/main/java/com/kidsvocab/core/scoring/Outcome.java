package com.kidsvocab.core.scoring;

/** Result of the first pass for one word. */
public enum Outcome {
    /** Solved for 2 or 1 points. */
    LEARNED,
    /** Solved for 0.5 points (third round or later, or a hint on the second round); includes problematic words. */
    HARD,
    /** Not solved by the end of the week. */
    UNLEARNED
}
