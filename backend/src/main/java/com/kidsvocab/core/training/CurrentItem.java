package com.kidsvocab.core.training;

/**
 * The word the child has to spell now.
 *
 * @param wordId           week word id
 * @param round            current round number, starting at 1
 * @param position         1-based position within the round
 * @param prompt           text in the child's native language
 * @param hintMask         primary answer with not-yet-revealed letters hidden; {@code null} until the first
 *                         hint, so the length of the word is not given away
 * @param hintAvailable    whether one more hint step is possible
 * @param roundSize        number of words in this round
 * @param remainingInRound words left in this round, including this one
 */
public record CurrentItem(long wordId, int round, int position, String prompt, String hintMask,
                          boolean hintAvailable, int roundSize, int remainingInRound) {
}
