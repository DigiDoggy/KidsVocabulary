package com.kidsvocab.core.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HintMaskTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "cat|0|___",
            "cat|1|c__",
            "cat|2|ca_",
            "cat|3|cat",
            "cat|9|cat",
            "ice cream|2|ic_ _____",
            "ice cream|4|ice c____",
            "t-shirt|1|t-_____",
            "don't|3|don'_",
    })
    void revealsLettersLeftToRightAndKeepsSeparators(String word, int revealed, String expected) {
        assertThat(HintMask.mask(word, revealed)).isEqualTo(expected);
    }

    @Test
    void handlesNonAsciiLetters() {
        assertThat(HintMask.mask("õun", 1)).isEqualTo("õ__");
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
        assertThatIllegalArgumentException().isThrownBy(() -> HintMask.mask("cat", -1));
    }
}
