package com.kidsvocab.core.training;

import com.kidsvocab.core.text.HintMask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Training run through the words of a week.
 * <ul>
 *   <li>Round 1 contains all words in random order; every word is asked once per round.</li>
 *   <li>Words spelled wrong move to the next round; the session ends when every word is solved.</li>
 *   <li>After {@value #PROBLEMATIC_AFTER_MISTAKES} wrong rounds a word becomes problematic and is
 *       placed by {@link RoundArranger}.</li>
 *   <li>Hints come in steps: the first shows only how many letters the word has, each further step
 *       reveals one more letter at a random position (reproducible from the seed, so a replayed
 *       session shows the same letters). Steps reset each round; the "hint used" flag sticks to the
 *       word.</li>
 *   <li>Submitting the same (word, round) again returns the stored result (idempotency).</li>
 *   <li>A blank answer is ignored and not counted as an attempt.</li>
 * </ul>
 * Not thread-safe; the application layer serialises access (optimistic locking).
 */
public final class TrainingSession {

    public static final int PROBLEMATIC_AFTER_MISTAKES = 3;

    public enum Status { IN_PROGRESS, COMPLETED, CLOSED }

    private final Map<Long, WordCard> cards = new LinkedHashMap<>();
    private final Map<Long, Progress> progress = new LinkedHashMap<>();
    private final Map<AttemptKey, AnswerResult> attempts = new HashMap<>();
    private final long seed;

    private Status status = Status.IN_PROGRESS;
    private int round;
    private List<Slot> slots = List.of();

    private TrainingSession(List<WordCard> words, long seed) {
        this.seed = seed;
        for (WordCard card : words) {
            if (cards.putIfAbsent(card.id(), card) != null) {
                throw new IllegalArgumentException("duplicate word id: " + card.id());
            }
            progress.put(card.id(), new Progress());
        }
    }

    public static TrainingSession start(List<WordCard> words, long seed) {
        if (words.isEmpty()) {
            throw new IllegalArgumentException("a session needs at least one word");
        }
        TrainingSession session = new TrainingSession(words, seed);
        session.startRound(1);
        return session;
    }

    public Status status() {
        return status;
    }

    public int round() {
        return round;
    }

    public long seed() {
        return seed;
    }

    /** Word ids of the current round in the order they are asked. */
    public List<Long> roundOrder() {
        return slots.stream().map(s -> s.wordId).toList();
    }

    public Optional<CurrentItem> current() {
        if (status != Status.IN_PROGRESS) {
            return Optional.empty();
        }
        Slot slot = currentSlot();
        WordCard card = cards.get(slot.wordId);
        String answer = card.primaryAnswer();
        int position = slots.indexOf(slot) + 1;
        return Optional.of(new CurrentItem(
                slot.wordId, round, position, card.prompt(), mask(slot, answer),
                slot.hintSteps < maxHintSteps(answer),
                slots.size(), slots.size() - position + 1));
    }

    public AnswerResult submit(long wordId, int roundNo, String answer) {
        AttemptKey key = new AttemptKey(wordId, roundNo);
        AnswerResult previous = attempts.get(key);
        if (previous != null) {
            return previous;
        }
        Slot slot = requireCurrent(wordId, roundNo);
        if (answer == null || answer.isBlank()) {
            return AnswerResult.ignored();
        }

        WordCard card = cards.get(wordId);
        Progress p = progress.get(wordId);
        boolean correct = card.accepts(answer);
        boolean becameProblematic = false;
        if (correct) {
            p.solvedRound = roundNo;
        } else {
            p.wrongCount++;
            if (p.wrongCount == PROBLEMATIC_AFTER_MISTAKES) {
                p.problematic = true;
                becameProblematic = true;
            }
        }
        slot.answered = true;

        boolean roundFinished = slots.stream().allMatch(s -> s.answered);
        boolean sessionFinished = false;
        if (roundFinished) {
            if (progress.values().stream().allMatch(Progress::isSolved)) {
                status = Status.COMPLETED;
                sessionFinished = true;
            } else {
                startRound(round + 1);
            }
        }
        AnswerResult result = new AnswerResult(
                correct ? AnswerResult.Verdict.CORRECT : AnswerResult.Verdict.WRONG,
                card.primaryAnswer(), becameProblematic, roundFinished, sessionFinished);
        attempts.put(key, result);
        return result;
    }

    /**
     * Takes the next hint step for the current word and returns the new mask: the first step shows the
     * word's length (all letters hidden), every further step reveals one more letter.
     */
    public String hint(long wordId, int roundNo) {
        Slot slot = requireCurrent(wordId, roundNo);
        String answer = cards.get(wordId).primaryAnswer();
        if (slot.hintSteps < maxHintSteps(answer)) {
            slot.hintSteps++;
            progress.get(wordId).hintUsed = true;
        }
        return mask(slot, answer);
    }

    /** The length step plus at most {@code letters - 1} revealed letters. */
    private static int maxHintSteps(String answer) {
        return 1 + HintMask.maxRevealable(answer);
    }

    /** No mask before the first hint step, so the length of the word stays unknown. */
    private String mask(Slot slot, String answer) {
        if (slot.hintSteps == 0) {
            return null;
        }
        long letterSeed = seed ^ (slot.wordId * 0xBF58476D1CE4E5B9L) ^ (round * 0x94D049BB133111EBL);
        return HintMask.mask(answer, HintMask.revealOrder(answer, letterSeed), slot.hintSteps - 1);
    }

    /** Stops an unfinished session, e.g. when the week is over. A completed session stays completed. */
    public void close() {
        if (status == Status.IN_PROGRESS) {
            status = Status.CLOSED;
        }
    }

    public List<WordState> wordStates() {
        List<WordState> states = new ArrayList<>(progress.size());
        progress.forEach((id, p) -> states.add(
                new WordState(id, p.wrongCount, p.hintUsed, p.problematic, p.solvedRound)));
        return states;
    }

    private void startRound(int roundNo) {
        round = roundNo;
        List<Long> regular = new ArrayList<>();
        List<Long> problematic = new ArrayList<>();
        progress.forEach((id, p) -> {
            if (!p.isSolved()) {
                (p.problematic ? problematic : regular).add(id);
            }
        });
        SplittableRandom rnd = new SplittableRandom(seed + roundNo * 0x9E3779B97F4A7C15L);
        slots = RoundArranger.arrange(regular, problematic, rnd).stream().map(Slot::new).toList();
    }

    private Slot currentSlot() {
        return slots.stream().filter(s -> !s.answered).findFirst().orElseThrow();
    }

    private Slot requireCurrent(long wordId, int roundNo) {
        if (status != Status.IN_PROGRESS) {
            throw new SessionNotActiveException(status);
        }
        Slot slot = currentSlot();
        if (roundNo != round || slot.wordId != wordId) {
            throw new TrainingConflictException(
                    "expected word " + slot.wordId + " in round " + round + ", got word " + wordId
                            + " in round " + roundNo);
        }
        return slot;
    }

    private record AttemptKey(long wordId, int round) {
    }

    private static final class Progress {
        int wrongCount;
        boolean hintUsed;
        boolean problematic;
        Integer solvedRound;

        boolean isSolved() {
            return solvedRound != null;
        }
    }

    private static final class Slot {
        final long wordId;
        /** 0 = no hint yet, 1 = length shown, n = length and n - 1 letters shown. */
        int hintSteps;
        boolean answered;

        Slot(long wordId) {
            this.wordId = wordId;
        }
    }
}
