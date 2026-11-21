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
                new SuggestedWord("chien", "dog", null)), "fr", "en", NONE)).hasSize(1);
        assertThat(validator.validate(List.of(
                new SuggestedWord("chien1", "dog", null)), "fr", "en", NONE)).isEmpty();
    }

    @Test
    void acceptsGermanWithUmlautsAndSharpSAndKeepsTheCapital() {
        List<ValidatedWord> result = validator.validate(List.of(
                new SuggestedWord("koer", "Hund", "animals"),
                new SuggestedWord("tänav", "Straße", null),
                new SuggestedWord("õun", "Äpfel", null)), "et", "de", NONE);

        assertThat(result).extracting(ValidatedWord::target).containsExactly("Hund", "Straße", "Äpfel");
        assertThat(result).extracting(ValidatedWord::targetNormalized).containsExactly("hund", "straße", "äpfel");
        assertThat(validator.validate(List.of(new SuggestedWord("vein", "vinõ", null)), "et", "de", NONE))
                .as("Estonian letters are not German").isEmpty();
    }

    @Test
    void passesTheEnglishMeaningOnNormalized() {
        List<ValidatedWord> result = validator.validate(List.of(
                new SuggestedWord("koer", "Hund", null, " Dog "),
                new SuggestedWord("kass", "Katze", null, "  "),
                new SuggestedWord("lind", "Vogel", null, null)), "et", "de", NONE);

        assertThat(result).extracting(ValidatedWord::englishNormalized).containsExactly("dog", null, null);
    }

    @Test
    void rejectsATranslationThatIsOnlyTheNativeWordInOtherLetters() {
        String ruMaus = word(0x043C, 0x0430, 0x0443, 0x0441);
        String ruHund = word(0x0445, 0x0443, 0x043D, 0x0434);
        String ruTaxi = word(0x0442, 0x0430, 0x043A, 0x0441, 0x0438);
        String ruPizza = word(0x043F, 0x0438, 0x0446, 0x0446, 0x0430);
        String ruFuss = word(0x0444, 0x0443, 0x0441, 0x0441);
        String ruMouse = word(0x043C, 0x044B, 0x0448, 0x044C);
        String ruCat = word(0x043A, 0x043E, 0x0448, 0x043A, 0x0430);

        assertThat(validator.validate(List.of(
                new SuggestedWord(ruMaus, "Maus", null),
                new SuggestedWord(ruHund, "Hund", null),
                new SuggestedWord(ruPizza, "Pizza", null),
                new SuggestedWord(ruFuss, "Fuß", null),
                new SuggestedWord(ruMouse, "Maus", null),
                new SuggestedWord(ruCat, "Katze", null)), "ru", "de", NONE))
                .extracting(ValidatedWord::target).containsExactly("Maus", "Katze");
        assertThat(validator.validate(List.of(
                new SuggestedWord(ruTaxi, "taxi", null),
                new SuggestedWord(RU_HEDGEHOG, "hedgehog", null)), "ru", "en", NONE))
                .extracting(ValidatedWord::target).containsExactly("hedgehog");
        assertThat(validator.validate(List.of(
                new SuggestedWord("kase", "Käse", null),
                new SuggestedWord("katse", "Katze", null),
                new SuggestedWord("taksi", "taxi", null),
                new SuggestedWord("hiir", "Maus", null)), "et", "de", NONE))
                .extracting(ValidatedWord::target).containsExactly("Maus");
    }

    @ParameterizedTest(name = "skeleton of [{0}] is [{1}]")
    @CsvSource({
            "Black-Jack's, blakjaks",
            "phoenix, foeniks",
            "khaki, haki",
            "quiz, kuis",
            "wäscheöl, vasheol",
            "šokolaad, shokolad",
            "žürii, shuri",
            "tõtt, tot",
    })
    void skeletonMergesLettersThatSoundAlike(String word, String skeleton) {
        assertThat(SuggestionValidator.skeleton(word.toLowerCase())).isEqualTo(skeleton);
    }

    @Test
    void skeletonKeepsLettersItDoesNotKnow() {
        assertThat(SuggestionValidator.skeleton(UK_LETTER)).isEqualTo("k" + word(0x0457) + "t");
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
