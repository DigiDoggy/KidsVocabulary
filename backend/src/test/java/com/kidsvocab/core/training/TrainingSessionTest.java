package com.kidsvocab.core.training;

import com.kidsvocab.core.training.AnswerResult.Verdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TrainingSessionTest {

    private static final Locale EN = Locale.ENGLISH;
    private static final long SEED = 2026L;

    private static final List<WordCard> WEEK = List.of(
            card(1, "koer", "dog"),
            card(2, "kass", "cat"),
            card(3, "jäätis", "ice cream"),
            card(4, "särk", "t-shirt"));
    private static final Map<Long, String> ANSWERS = WEEK.stream()
            .collect(Collectors.toMap(WordCard::id, WordCard::primaryAnswer));

    // --- rounds ---------------------------------------------------------------------------------

    @Test
    void allWordsCorrectInFirstRoundCompletesTheSession() {
        TrainingSession session = TrainingSession.start(WEEK, SEED);

        List<AnswerResult> results = playRound(session, Set.of());

        assertThat(results).hasSize(4);
        assertThat(results).allMatch(AnswerResult::isCorrect);
        assertThat(results.subList(0, 3)).noneMatch(AnswerResult::roundFinished);
        assertThat(results.getLast().roundFinished()).isTrue();
        assertThat(results.getLast().sessionFinished()).isTrue();
        assertThat(session.status()).isEqualTo(TrainingSession.Status.COMPLETED);
        assertThat(session.current()).isEmpty();
        assertThat(session.wordStates())
                .extracting(WordState::solvedRound, WordState::wrongCount)
                .containsOnly(org.assertj.core.groups.Tuple.tuple(1, 0));
    }

    @Test
    void firstRoundContainsEveryWordInSeedDeterminedOrder() {
        TrainingSession a = TrainingSession.start(WEEK, SEED);
        TrainingSession b = TrainingSession.start(WEEK, SEED);

        assertThat(a.roundOrder()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(a.roundOrder()).isEqualTo(b.roundOrder());
        assertThat(a.round()).isEqualTo(1);
        assertThat(a.seed()).isEqualTo(SEED);
    }

    @Test
    void wrongWordsMoveToTheNextRound() {
        TrainingSession session = TrainingSession.start(WEEK, SEED);

        List<AnswerResult> round1 = playRound(session, Set.of(2L, 4L));

        assertThat(round1.getLast().roundFinished()).isTrue();
        assertThat(round1.getLast().sessionFinished()).isFalse();
        assertThat(session.round()).isEqualTo(2);
        assertThat(session.roundOrder()).containsExactlyInAnyOrder(2L, 4L);

        playRound(session, Set.of(4L));
        assertThat(session.roundOrder()).containsExactly(4L);

        playRound(session, Set.of());
        assertThat(session.status()).isEqualTo(TrainingSession.Status.COMPLETED);
        assertThat(stateOf(session, 1)).isEqualTo(new WordState(1, 0, false, false, 1));
        assertThat(stateOf(session, 2)).isEqualTo(new WordState(2, 1, false, false, 2));
        assertThat(stateOf(session, 4)).isEqualTo(new WordState(4, 2, false, false, 3));
    }

    @Test
    void wrongAnswerRevealsTheCorrectSpelling() {
        TrainingSession session = TrainingSession.start(List.of(card(2, "kass", "cat")), SEED);

        AnswerResult result = session.submit(2, 1, "kat");

        assertThat(result.verdict()).isEqualTo(Verdict.WRONG);
        assertThat(result.isCorrect()).isFalse();
        assertThat(result.correctAnswer()).isEqualTo("cat");
    }

    @Test
    void wordBecomesProblematicExactlyOnTheThirdMistake() {
        TrainingSession session = TrainingSession.start(List.of(card(2, "kass", "cat")), SEED);

        assertThat(session.submit(2, 1, "kat").becameProblematic()).isFalse();
        assertThat(session.submit(2, 2, "kat").becameProblematic()).isFalse();
        assertThat(session.submit(2, 3, "kat").becameProblematic()).isTrue();
        assertThat(stateOf(session, 2).problematic()).isTrue();

        assertThat(session.submit(2, 4, "kat").becameProblematic()).isFalse();
        assertThat(session.submit(2, 5, "cat").sessionFinished()).isTrue();
        assertThat(stateOf(session, 2)).isEqualTo(new WordState(2, 4, false, true, 5));
    }

    @Test
    void acceptsSynonyms() {
        TrainingSession session = TrainingSession.start(
                List.of(WordCard.of(5, "värv", "colour", List.of("color"), EN)), SEED);

        assertThat(session.submit(5, 1, "Color ").isCorrect()).isTrue();
    }

    // --- current item ---------------------------------------------------------------------------

    @Test
    void currentItemDescribesTheWordToSpell() {
        TrainingSession session = TrainingSession.start(WEEK, SEED);
        long firstId = session.roundOrder().getFirst();
        WordCard first = WEEK.stream().filter(c -> c.id() == firstId).findFirst().orElseThrow();

        CurrentItem item = session.current().orElseThrow();

        assertThat(item.wordId()).isEqualTo(firstId);
        assertThat(item.round()).isEqualTo(1);
        assertThat(item.position()).isEqualTo(1);
        assertThat(item.prompt()).isEqualTo(first.prompt());
        assertThat(item.hintMask()).as("the length stays secret until the first hint").isNull();
        assertThat(item.hintAvailable()).isTrue();
        assertThat(item.roundSize()).isEqualTo(4);
        assertThat(item.remainingInRound()).isEqualTo(4);

        session.submit(firstId, 1, ANSWERS.get(firstId));
        CurrentItem second = session.current().orElseThrow();
        assertThat(second.position()).isEqualTo(2);
        assertThat(second.remainingInRound()).isEqualTo(3);
    }

    // --- hints ----------------------------------------------------------------------------------

    @Test
    void firstHintShowsOnlyTheLengthThenEachHintRevealsOneLetter() {
        TrainingSession session = TrainingSession.start(List.of(card(2, "kass", "cat")), SEED);

        assertThat(session.hint(2, 1)).isEqualTo("___");
        assertThat(stateOf(session, 2).hintUsed()).as("the length step already counts as a hint").isTrue();
        assertThat(session.current().orElseThrow().hintAvailable()).isTrue();

        String one = session.hint(2, 1);
        assertThat(one).hasSize(3).matches(letterOrGap("cat")).matches(revealed(1));
        assertThat(session.current().orElseThrow().hintAvailable()).isTrue();

        String two = session.hint(2, 1);
        assertThat(two).matches(letterOrGap("cat")).matches(revealed(2));
        for (int i = 0; i < one.length(); i++) {
            if (one.charAt(i) != '_') assertThat(two.charAt(i)).as("revealed letters stay").isEqualTo(one.charAt(i));
        }
        assertThat(session.current().orElseThrow().hintAvailable()).isFalse();

        assertThat(session.hint(2, 1)).as("never the whole word").isEqualTo(two);
        assertThat(session.current().orElseThrow().hintMask()).isEqualTo(two);
    }

    @Test
    void lettersAreRevealedAtRandomButReproduciblePositions() {
        List<WordCard> week = List.of(card(9, "elevant", "elephant"));
        Set<String> firstLetters = new java.util.HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            TrainingSession a = TrainingSession.start(week, seed);
            TrainingSession b = TrainingSession.start(week, seed);
            a.hint(9, 1);
            b.hint(9, 1);
            String mask = a.hint(9, 1);
            assertThat(b.hint(9, 1)).as("same seed, same letter").isEqualTo(mask);
            firstLetters.add(mask);
        }
        assertThat(firstLetters).as("not always the first letter").hasSizeGreaterThan(1);
    }

    @Test
    void hintStepsResetInTheNextRoundButHintFlagSticks() {
        TrainingSession session = TrainingSession.start(List.of(card(2, "kass", "cat")), SEED);
        session.hint(2, 1);
        session.hint(2, 1);
        session.submit(2, 1, "cot");

        CurrentItem item = session.current().orElseThrow();
        assertThat(item.round()).isEqualTo(2);
        assertThat(item.hintMask()).isNull();
        assertThat(item.hintAvailable()).isTrue();

        session.submit(2, 2, "cat");
        assertThat(stateOf(session, 2)).isEqualTo(new WordState(2, 1, true, false, 2));
    }

    @Test
    void oneLetterWordsGetOnlyTheLengthHint() {
        TrainingSession session = TrainingSession.start(List.of(card(6, "mina", "I")), SEED);

        assertThat(session.current().orElseThrow().hintAvailable()).isTrue();
        assertThat(session.hint(6, 1)).isEqualTo("_");
        assertThat(stateOf(session, 6).hintUsed()).isTrue();
        assertThat(session.current().orElseThrow().hintAvailable()).isFalse();
        assertThat(session.hint(6, 1)).isEqualTo("_");
    }

    private static String letterOrGap(String word) {
        StringBuilder regex = new StringBuilder();
        for (char c : word.toCharArray()) {
            regex.append('[').append(c).append("_]");
        }
        return regex.toString();
    }

    private static String revealed(int letters) {
        return "^_*(?:[^_]_*){" + letters + "}$";
    }

    // --- robustness -----------------------------------------------------------------------------

    @Test
    void blankAnswerIsIgnored() {
        TrainingSession session = TrainingSession.start(List.of(card(2, "kass", "cat")), SEED);

        AnswerResult blank = session.submit(2, 1, "   ");
        AnswerResult missing = session.submit(2, 1, null);

        assertThat(blank).isEqualTo(new AnswerResult(Verdict.IGNORED, null, false, false, false));
        assertThat(missing.verdict()).isEqualTo(Verdict.IGNORED);
        assertThat(stateOf(session, 2).wrongCount()).isZero();
        assertThat(session.submit(2, 1, "cat").isCorrect()).isTrue();
    }

    @Test
    void repeatedSubmissionReturnsTheStoredResult() {
        TrainingSession session = TrainingSession.start(List.of(card(2, "kass", "cat")), SEED);
        AnswerResult first = session.submit(2, 1, "kat");

        AnswerResult repeat = session.submit(2, 1, "cat");

        assertThat(repeat).isSameAs(first);
        assertThat(session.round()).isEqualTo(2);
        assertThat(stateOf(session, 2).wrongCount()).isEqualTo(1);

        AnswerResult last = session.submit(2, 2, "cat");
        assertThat(session.submit(2, 2, "cat")).isSameAs(last);
    }

    @Test
    void answeringAWordThatIsNotCurrentIsAConflict() {
        TrainingSession session = TrainingSession.start(WEEK, SEED);
        long current = session.roundOrder().getFirst();
        long other = session.roundOrder().get(1);

        assertThatExceptionOfType(TrainingConflictException.class)
                .isThrownBy(() -> session.submit(other, 1, "dog"));
        assertThatExceptionOfType(TrainingConflictException.class)
                .isThrownBy(() -> session.submit(current, 2, "dog"));
        assertThatExceptionOfType(TrainingConflictException.class)
                .isThrownBy(() -> session.hint(other, 1));
    }

    @Test
    void finishedSessionRejectsNewAnswersAndHints() {
        TrainingSession session = TrainingSession.start(List.of(card(2, "kass", "cat")), SEED);
        session.submit(2, 1, "cat");

        assertThatExceptionOfType(SessionNotActiveException.class)
                .isThrownBy(() -> session.submit(2, 2, "cat"))
                .withMessageContaining("COMPLETED");
        assertThatExceptionOfType(SessionNotActiveException.class)
                .isThrownBy(() -> session.hint(2, 2));
    }

    @Test
    void closingStopsAnUnfinishedSession() {
        TrainingSession session = TrainingSession.start(WEEK, SEED);

        session.close();

        assertThat(session.status()).isEqualTo(TrainingSession.Status.CLOSED);
        assertThat(session.current()).isEmpty();
        long first = session.roundOrder().getFirst();
        assertThatExceptionOfType(SessionNotActiveException.class)
                .isThrownBy(() -> session.submit(first, 1, ANSWERS.get(first)))
                .withMessageContaining("CLOSED");
    }

    @Test
    void closingDoesNotChangeACompletedSession() {
        TrainingSession session = TrainingSession.start(List.of(card(2, "kass", "cat")), SEED);
        session.submit(2, 1, "cat");

        session.close();

        assertThat(session.status()).isEqualTo(TrainingSession.Status.COMPLETED);
    }

    @Test
    void sessionNeedsDistinctWords() {
        assertThatIllegalArgumentException().isThrownBy(() -> TrainingSession.start(List.of(), SEED));
        assertThatIllegalArgumentException().isThrownBy(() -> TrainingSession.start(
                List.of(card(1, "koer", "dog"), card(1, "kass", "cat")), SEED));
    }

    // --- helpers --------------------------------------------------------------------------------

    private static WordCard card(long id, String prompt, String answer) {
        return WordCard.of(id, prompt, answer, List.of(), EN);
    }

    /** Answers every word of the current round; words in {@code failIds} are answered wrong. */
    private static List<AnswerResult> playRound(TrainingSession session, Set<Long> failIds) {
        Function<Long, String> answerFor = id -> failIds.contains(id) ? "wrong" : ANSWERS.get(id);
        int round = session.round();
        List<AnswerResult> results = new ArrayList<>();
        for (long id : session.roundOrder()) {
            results.add(session.submit(id, round, answerFor.apply(id)));
        }
        return results;
    }

    private static WordState stateOf(TrainingSession session, long wordId) {
        return session.wordStates().stream().filter(s -> s.wordId() == wordId).findFirst().orElseThrow();
    }
}
