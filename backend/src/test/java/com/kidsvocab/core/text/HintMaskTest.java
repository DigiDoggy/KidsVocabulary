package com.kidsvocab.core.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HintMaskTest {

    private static int[] order(String csv) {
        return Arrays.stream(csv.split(" ")).mapToInt(Integer::parseInt).toArray();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "cat|0 1 2|0|___",
            "cat|2 0 1|1|__t",
            "cat|2 0 1|2|c_t",
            "cat|2 0 1|3|cat",
            "cat|2 0 1|9|cat",
            "ice cream|7 3 0 1 2 4 5 6|2|___ c___m",
            "t-shirt|1 0 2 3 4 5|1|_-s____",
            "don't|3 0 1 2|1|___'t",
    })
    void revealsLettersInTheGivenOrderAndKeepsSeparators(String word, String order, int revealed, String expected) {
        assertThat(HintMask.mask(word, order(order), revealed)).isEqualTo(expected);
    }

    @Test
    void handlesNonAsciiLetters() {
        assertThat(HintMask.mask("õun", order("0 1 2"), 1)).isEqualTo("õ__");
    }

    @Test
    void revealOrderIsAReproduciblePermutationOfTheLetters() {
        int[] first = HintMask.revealOrder("ice cream", 42);

        assertThat(first).hasSize(8);
        assertThat(Arrays.stream(first).sorted().toArray()).containsExactly(IntStream.range(0, 8).toArray());
        assertThat(HintMask.revealOrder("ice cream", 42)).containsExactly(first);
    }

    @Test
    void revealOrderDependsOnTheSeed() {
        long distinct = IntStream.range(0, 20)
                .mapToObj(seed -> Arrays.toString(HintMask.revealOrder("elephant", seed)))
                .distinct().count();
        assertThat(distinct).isGreaterThan(1);
    }

    @Test
    void revealOrderOfAWordWithoutLettersIsEmpty() {
        assertThat(HintMask.revealOrder("", 1)).isEmpty();
        assertThat(HintMask.revealOrder("I", 1)).containsExactly(0);
    }

    @Test
    void countsOnlyLetters() {
        assertThat(HintMask.letterCount("ice cream")).isEqualTo(8);
        assertThat(HintMask.letterCount("t-shirt")).isEqualTo(6);
    }

    @Test
    void neverAllowsRevealingTheWholeWord() {
        assertThat(HintMask.maxRevealable("cat")).isEqualTo(2);
        assertThat(HintMask.maxRevealable("I")).isZero();
        assertThat(HintMask.maxRevealable("")).isZero();
    }

    @Test
    void rejectsNegativeCount() {
        assertThatIllegalArgumentException().isThrownBy(() -> HintMask.mask("cat", order("0 1 2"), -1));
    }
}
