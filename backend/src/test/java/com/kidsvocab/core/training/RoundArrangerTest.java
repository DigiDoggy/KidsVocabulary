package com.kidsvocab.core.training;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RoundArrangerTest {

    @Test
    void withoutProblematicWordsReturnsShuffledRegularWords() {
        List<String> result = RoundArranger.arrange(List.of("a", "b", "c"), List.of(), new SplittableRandom(1));

        assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void withOnlyProblematicWordsReturnsThemShuffled() {
        List<String> result = RoundArranger.arrange(List.of(), List.of("x", "y"), new SplittableRandom(1));

        assertThat(result).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    void placesProblematicWordsInsideAndApart() {
        List<String> result = RoundArranger.arrange(
                List.of("a", "b", "c", "d", "e"), List.of("x", "y", "z"), new SplittableRandom(7));

        assertThat(result).hasSize(8);
        assertThat(isProblematic(result.getFirst())).isFalse();
        assertThat(isProblematic(result.getLast())).isFalse();
        assertThat(maxAdjacentProblematic(result)).isEqualTo(1);
    }

    @Test
    void allowsEdgesWhenThereAreNotEnoughInnerGaps() {
        List<String> result = RoundArranger.arrange(List.of("a", "b"), List.of("x", "y", "z"), new SplittableRandom(3));

        // 3 problematic words, 2 regular: the only non-adjacent layout is P r P r P
        assertThat(result).hasSize(5);
        assertThat(IntStream.range(0, 5).mapToObj(i -> isProblematic(result.get(i))))
                .containsExactly(true, false, true, false, true);
    }

    @Test
    void spreadsProblematicWordsEvenlyWhenTheyCannotBeSeparated() {
        List<String> result = RoundArranger.arrange(
                List.of("a"), List.of("v", "w", "x", "y", "z"), new SplittableRandom(5));

        int before = result.indexOf("a");
        int after = result.size() - before - 1;
        assertThat(result).hasSize(6);
        assertThat(Math.abs(before - after)).isLessThanOrEqualTo(1);
    }

    @Test
    void sameRandomSeedGivesSameOrder() {
        List<Integer> regular = IntStream.range(0, 10).boxed().toList();
        List<Integer> problematic = IntStream.range(100, 104).boxed().toList();

        assertThat(RoundArranger.arrange(regular, problematic, new SplittableRandom(42)))
                .isEqualTo(RoundArranger.arrange(regular, problematic, new SplittableRandom(42)));
    }

    @Property
    void arrangementRespectsTheRules(@ForAll @IntRange(max = 12) int regularCount,
                                     @ForAll @IntRange(max = 12) int problematicCount,
                                     @ForAll long seed) {
        List<String> regular = names("r", regularCount);
        List<String> problematic = names("x", problematicCount);

        List<String> result = RoundArranger.arrange(regular, problematic, new SplittableRandom(seed));

        List<String> all = new ArrayList<>(regular);
        all.addAll(problematic);
        assertThat(result).containsExactlyInAnyOrderElementsOf(all);
        if (regularCount == 0 || problematicCount == 0) {
            return;
        }
        if (problematicCount <= regularCount - 1) {
            assertThat(isProblematic(result.getFirst())).isFalse();
            assertThat(isProblematic(result.getLast())).isFalse();
        }
        if (problematicCount <= regularCount + 1) {
            assertThat(maxAdjacentProblematic(result)).isEqualTo(1);
        } else {
            int perGapFloor = problematicCount / (regularCount + 1);
            assertThat(maxAdjacentProblematic(result)).isBetween(perGapFloor, perGapFloor + 1);
        }
    }

    private static List<String> names(String prefix, int count) {
        return IntStream.range(0, count).mapToObj(i -> prefix + i).toList();
    }

    private static boolean isProblematic(String word) {
        return !word.startsWith("r") && !word.matches("[a-e]");
    }

    private static int maxAdjacentProblematic(List<String> words) {
        int max = 0;
        int run = 0;
        for (String w : words) {
            run = isProblematic(w) ? run + 1 : 0;
            max = Math.max(max, run);
        }
        return max;
    }
}
