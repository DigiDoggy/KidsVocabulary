package com.kidsvocab.core.training;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class WordCardTest {

    private static final Locale EN = Locale.ENGLISH;

    @Test
    void acceptsPrimaryAnswerIgnoringCaseAndSurroundingSpaces() {
        WordCard card = WordCard.of(1, "koer", "dog", List.of(), EN);

        assertThat(card.accepts(" Dog ")).isTrue();
        assertThat(card.accepts("dgo")).isFalse();
        assertThat(card.accepts(null)).isFalse();
    }

    @Test
    void acceptsSynonymsAndSpellingVariants() {
        WordCard card = WordCard.of(1, "värv", "colour", List.of("Color", " ", "colour"), EN);

        assertThat(card.acceptedAnswers()).containsExactlyInAnyOrder("colour", "color");
        assertThat(card.accepts("COLOR")).isTrue();
    }

    @Test
    void rejectsBlankPrompt() {
        assertThatIllegalArgumentException().isThrownBy(() -> WordCard.of(1, null, "dog", List.of(), EN));
        assertThatIllegalArgumentException().isThrownBy(() -> WordCard.of(1, "  ", "dog", List.of(), EN));
    }

    @Test
    void rejectsBlankPrimaryAnswer() {
        assertThatIllegalArgumentException().isThrownBy(() -> WordCard.of(1, "koer", "", List.of(), EN));
    }

    @Test
    void requiresLocale() {
        assertThatNullPointerException().isThrownBy(() -> new WordCard(1, "koer", "dog", Set.of("dog"), null));
    }

    @Test
    void requiresAtLeastOneAcceptedAnswer() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WordCard(1, "koer", "dog", Set.of(), EN));
    }
}
