package com.kidsvocab.core.suggestion;

import com.kidsvocab.core.text.AnswerNormalizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Checks words proposed by an external source before they reach a child:
 * <ul>
 *   <li>both parts present, at most {@value #MAX_LENGTH} characters and {@value #MAX_WORDS} words;</li>
 *   <li>only letters of the language's alphabet plus space, hyphen and apostrophe;</li>
 *   <li>the translation differs from the native word;</li>
 *   <li>no stop-listed words (anything unsuitable for children);</li>
 *   <li>no duplicates within the response and nothing from the exclusion lists.</li>
 * </ul>
 * Topics outside the whitelist are dropped (set to {@code null}) rather than rejecting the word.
 */
public final class SuggestionValidator {

    public static final int MAX_LENGTH = 20;
    public static final int MAX_WORDS = 2;

    private static final IntPredicate LATIN = cp -> cp >= 'a' && cp <= 'z';
    private static final Map<String, IntPredicate> ALPHABETS = Map.of(
            "en", LATIN,
            "et", LATIN.or(cp -> "õäöüšž".indexOf(cp) >= 0),
            "ru", cp -> (cp >= 0x0430 && cp <= 0x044F) || cp == 0x0451);   // a..ya, yo

    private final Set<String> stopWords;
    private final Set<String> topics;

    /**
     * @param stopWords lower-case words that must not appear in either part of a suggestion
     * @param topics    allowed topic names
     */
    public SuggestionValidator(Set<String> stopWords, Set<String> topics) {
        this.stopWords = Set.copyOf(stopWords);
        this.topics = Set.copyOf(topics);
    }

    /** What the caller already has, so it must not be suggested again. */
    public record Exclusions(Set<String> sources, Set<String> targets) {

        public Exclusions {
            sources = Set.copyOf(sources);
            targets = Set.copyOf(targets);
        }
    }

    public List<ValidatedWord> validate(List<SuggestedWord> words, String nativeLang, String targetLang,
                                        Exclusions exclusions) {
        Locale nativeLocale = Locale.forLanguageTag(nativeLang);
        Locale targetLocale = Locale.forLanguageTag(targetLang);
        Set<String> seenSources = new HashSet<>(exclusions.sources());
        Set<String> seenTargets = new HashSet<>(exclusions.targets());
        List<ValidatedWord> valid = new ArrayList<>();
        for (SuggestedWord word : words) {
            if (word == null) {
                continue;
            }
            String source = AnswerNormalizer.normalize(word.source(), nativeLocale);
            String target = AnswerNormalizer.normalize(word.target(), targetLocale);
            if (!wellFormed(source, nativeLang) || !wellFormed(target, targetLang)
                    || source.equals(target) || containsStopWord(source) || containsStopWord(target)
                    || !seenSources.add(source) || !seenTargets.add(target)) {
                continue;
            }
            valid.add(new ValidatedWord(word.source().strip(), word.target().strip(), source, target,
                    topicOf(word.topic())));
        }
        return valid;
    }

    private static boolean wellFormed(String normalized, String lang) {
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH
                || normalized.split(" ").length > MAX_WORDS) {
            return false;
        }
        IntPredicate letter = ALPHABETS.getOrDefault(lang, Character::isLetter);
        return normalized.codePoints().allMatch(cp -> letter.test(cp) || cp == ' ' || cp == '-' || cp == '\'');
    }

    private boolean containsStopWord(String normalized) {
        for (String part : normalized.split("[ '-]")) {
            if (stopWords.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private String topicOf(String topic) {
        String t = Objects.requireNonNullElse(topic, "").strip().toLowerCase(Locale.ROOT);
        return topics.contains(t) ? t : null;
    }
}
