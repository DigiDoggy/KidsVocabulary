package com.kidsvocab.core.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmojiTest {

    private static String of(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    @Test
    void acceptsSingleEmojiOfEveryShape() {
        assertThat(Emoji.clean(of(0x1F34E))).as("apple").isEqualTo(of(0x1F34E));
        assertThat(Emoji.clean(of(0x2744, 0xFE0F))).as("snowflake with variation selector").isEqualTo(of(0x2744, 0xFE0F));
        assertThat(Emoji.clean(of(0x26BD))).as("ball, presentation emoji in the BMP").isEqualTo(of(0x26BD));
        assertThat(Emoji.clean(of(0x1F9D1, 0x200D, 0x1F3EB))).as("teacher, ZWJ sequence").isNotNull();
        assertThat(Emoji.clean(of(0x1F44D, 0x1F3FD))).as("thumbs up with skin tone").isNotNull();
        assertThat(Emoji.clean(of(0x1F1EA, 0x1F1EA))).as("flag").isNotNull();
        assertThat(Emoji.clean(of('1', 0xFE0F, 0x20E3))).as("keycap").isNotNull();
        assertThat(Emoji.clean(of('#', 0x20E3))).as("keycap without variation selector").isNotNull();
    }

    @Test
    void trimsSurroundingSpace() {
        assertThat(Emoji.clean("  " + of(0x1F415) + "\n")).isEqualTo(of(0x1F415));
    }

    @Test
    void blankOrMissingIsNoEmoji() {
        assertThat(Emoji.clean(null)).isNull();
        assertThat(Emoji.clean("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "1", "dog", "#", "*", "©"})
    void rejectsTextAndSymbols(String value) {
        assertThat(Emoji.clean(value)).isNull();
    }

    @Test
    void rejectsMoreThanOneEmojiOrEmojiWithText() {
        assertThat(Emoji.clean(of(0x1F415, 0x1F415))).isNull();
        assertThat(Emoji.clean(of(0x1F415) + "a")).isNull();
        assertThat(Emoji.clean(of('a', 0xFE0F))).as("variation selector after a letter").isNull();
        assertThat(Emoji.clean(of(0xFE0F))).as("variation selector alone").isNull();
        assertThat(Emoji.clean(of(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466, 0x200D,
                0x1F468, 0x200D, 0x1F469))).as("too long").isNull();
    }
}
