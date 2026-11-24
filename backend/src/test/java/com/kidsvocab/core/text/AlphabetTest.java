package com.kidsvocab.core.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphabetTest {

    @Test
    void lettersOfEachLanguage() {
        assertThat(Alphabet.of("en")).hasSize(26).startsWith("abc").endsWith("xyz");
        assertThat(Alphabet.of("de")).hasSize(30).contains("ß");
        assertThat(Alphabet.of("et")).hasSize(32);
        assertThat(Alphabet.of("ru")).hasSize(33).startsWith(new String(Character.toChars(0x0430)));
    }

    @Test
    void unknownLanguage() {
        assertThatThrownBy(() -> Alphabet.of("xx")).isInstanceOf(IllegalArgumentException.class);
    }
}
