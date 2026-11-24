package com.kidsvocab.core.game;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class ChoicesTest {

    @Test
    void fourOptionsWithTheAnswerAmongThem() {
        List<String> options = Choices.options("dog",
                List.of(List.of("cat", "cow", "pig", "sheep")), new SplittableRandom(1));
        assertThat(options).hasSize(Choices.OPTIONS).contains("dog").doesNotHaveDuplicates();
        assertThat(options).isSubsetOf("dog", "cat", "cow", "pig", "sheep");
    }

    @Test
    void earlierTiersComeFirst() {
        for (long seed = 0; seed < 20; seed++) {
            List<String> options = Choices.options("dog",
                    List.of(List.of("cat", "cow"), List.of("pig"), List.of("tree", "house")),
                    new SplittableRandom(seed));
            assertThat(options).containsExactlyInAnyOrder("dog", "cat", "cow", "pig");
        }
    }

    @Test
    void skipsBlanksRepeatsAndTheAnswerItself() {
        List<String> options = Choices.options("Hund",
                List.of(Arrays.asList(null, " ", "hund", "Katze", "katze "), List.of(" Maus ")),
                new SplittableRandom(3));
        assertThat(options).containsExactlyInAnyOrder("Hund", "Katze", "Maus");
    }

    @Test
    void fewerOptionsWhenThereIsNothingElse() {
        assertThat(Choices.options("dog", List.of(), new SplittableRandom(1))).containsExactly("dog");
    }

    @Test
    void theSameSeedGivesTheSameOrder() {
        List<List<String>> tiers = List.of(List.of("a1", "a2", "a3", "a4", "a5"));
        assertThat(Choices.options("x", tiers, new SplittableRandom(9)))
                .isEqualTo(Choices.options("x", tiers, new SplittableRandom(9)));
    }
}
