package com.kidsvocab.core.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AnswerNormalizerTest {

    private static final Locale EN = Locale.ENGLISH;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Dog|dog",
            "'  dog  '|dog",
            "DOG|dog",
            "'ice   cream'|ice cream",
            "'\tice\tcream\n'|ice cream",
    })
    void ignoresCaseAndWhitespace(String input, String expected) {
        assertThat(AnswerNormalizer.normalize(input, EN)).isEqualTo(expected);
    }

    @Test
    void treatsNonBreakingSpaceAsWhitespace() {
        assertThat(AnswerNormalizer.normalize(" ice cream ", EN)).isEqualTo("ice cream");
    }

    @Test
    void unifiesApostrophes() {
        assertThat(AnswerNormalizer.normalize("don’t", EN)).isEqualTo("don't");
        assertThat(AnswerNormalizer.normalize("don‘t", EN)).isEqualTo("don't");
        assertThat(AnswerNormalizer.normalize("don`t", EN)).isEqualTo("don't");
    }

    @Test
    void unifiesDashes() {
        assertThat(AnswerNormalizer.normalize("T–shirt", EN)).isEqualTo("t-shirt");
        assertThat(AnswerNormalizer.normalize("T—shirt", EN)).isEqualTo("t-shirt");
    }

    @Test
    void composesUnicodeToNfc() {
        assertThat(AnswerNormalizer.normalize("Café", EN)).isEqualTo("café");
    }

    @Test
    void lowercasesUsingTheGivenLocale() {
        assertThat(AnswerNormalizer.normalize("ÕUN", Locale.forLanguageTag("et"))).isEqualTo("õun");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(AnswerNormalizer.normalize(null, EN)).isEmpty();
    }

    @Test
    void localeIsRequired() {
        assertThatNullPointerException().isThrownBy(() -> AnswerNormalizer.normalize("dog", null));
    }
}
