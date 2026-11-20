package com.kidsvocab.core.suggestion;

import com.kidsvocab.core.suggestion.SuggestionValidator.Exclusions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionValidatorTest {

    private static final Exclusions NONE = new Exclusions(Set.of(), Set.of());
    /** Russian test words built from code points, so the source stays ASCII. */
    private static final String RU_HEDGEHOG = word(0x0451, 0x0436);
    private static final String RU_CAT = word(0x043A, 0x043E, 0x0442);
    private static final String RU_CAT_LATIN_O = word(0x043A, 'o', 0x0442);
    private static final String UK_LETTER = word(0x043A, 0x0457, 0x0442);
    private final SuggestionValidator validator =
            new SuggestionValidator(Set.of("beer", "gun"), Set.of("animals", "food"));

    @Test
    void acceptsWellFormedWordsAndNormalizesThem() {
        List<ValidatedWord> result = validator.validate(
                List.of(new SuggestedWord(" Koer ", "Dog", "Animals")), "et", "en", NONE);

        assertThat(result).containsExactly(new ValidatedWord("Koer", "Dog", "koer", "dog", "animals"));
    }

    @Test
    void keepsWordsWithUnknownTopicButDropsTheTopic() {
        assertThat(validator.validate(List.of(
                        new SuggestedWord("kass", "cat", "pets"),
                        new SuggestedWord("lehm", "cow", null)), "et", "en", NONE))
                .extracting(ValidatedWord::topic)
                .containsExactly(null, null);
    }

    @ParameterizedTest(name = "[{0}] -> [{1}] rejected")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "NULL|dog",
            "koer|NULL",
            "' '|dog",
            "koer|'  '",
            "koer|dog2",             // digits
            "koer|dog!",             // punctuation
            "koer|dög",         // letter outside the English alphabet
            "koer|very big brown dog",  // too many words
            "koer|abcdefghijklmnopqrstu", // too long
            "dog|dog",               // translation equals the word
            "õlu|beer",         // stop word
            "koer|big gun",          // stop word inside a phrase
            "beer|drink",            // stop word in the native part
    })
    void rejectsMalformedOrUnsuitableWords(String source, String target) {
        assertThat(validator.validate(Arrays.asList(new SuggestedWord(source, target, "animals")), "et", "en", NONE))
                .isEmpty();
    }

    @Test
    void acceptsSeparatorsAndLanguageSpecificLetters() {
        List<ValidatedWord> result = validator.validate(List.of(
                new SuggestedWord("jäätis", "ice cream", "food"),
                new SuggestedWord("t-särk", "T-shirt", null),
                new SuggestedWord("šokolaad", "chocolate", null)), "et", "en", NONE);

        assertThat(result).extracting(ValidatedWord::targetNormalized)
                .containsExactly("ice cream", "t-shirt", "chocolate");
    }

    @Test
    void supportsRussianAndUnknownLanguages() {
        assertThat(validator.validate(List.of(
                new SuggestedWord(RU_HEDGEHOG, "hedgehog", null)), "ru", "en", NONE)).hasSize(1);
        assertThat(validator.validate(List.of(
                new SuggestedWord("hund", "dog", null)), "de", "en", NONE)).hasSize(1);
        assertThat(validator.validate(List.of(
                new SuggestedWord("hund1", "dog", null)), "de", "en", NONE)).isEmpty();
    }

    @Test
    void checksTheAlphabetOfEachLanguage() {
        assertThat(validator.validate(List.of(new SuggestedWord(RU_CAT, "cat", null)), "et", "en", NONE)).isEmpty();
        assertThat(validator.validate(List.of(new SuggestedWord(RU_CAT, "cat", null)), "ru", "en", NONE)).hasSize(1);
        assertThat(validator.validate(List.of(new SuggestedWord(RU_CAT_LATIN_O, "cat", null)), "ru", "en", NONE))
                .isEmpty();
        assertThat(validator.validate(List.of(new SuggestedWord(UK_LETTER, "cat", null)), "ru", "en", NONE)).isEmpty();
    }

    @Test
    void acceptsApostrophes() {
        assertThat(validator.validate(List.of(new SuggestedWord("ei tohi", "don't", null)), "et", "en", NONE))
                .extracting(ValidatedWord::targetNormalized).containsExactly("don't");
    }

    @Test
    void removesDuplicatesAndExcludedWords() {
        Exclusions exclusions = new Exclusions(Set.of("kass"), Set.of("cow"));

        List<ValidatedWord> result = validator.validate(List.of(
                new SuggestedWord("koer", "dog", null),
                new SuggestedWord("Koer", "hound", null),   // same native word
                new SuggestedWord("kutsu", "DOG", null),    // same translation
                new SuggestedWord("kass", "cat", null),     // excluded native word
                new SuggestedWord("lehm", "cow", null),     // excluded translation
                new SuggestedWord("siga", "pig", null)), "et", "en", exclusions);

        assertThat(result).extracting(ValidatedWord::targetNormalized).containsExactly("dog", "pig");
    }

    @Test
    void skipsNullEntries() {
        assertThat(validator.validate(Arrays.asList(null, new SuggestedWord("koer", "dog", null)), "et", "en", NONE))
                .hasSize(1);
    }

    private static String word(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }
}
