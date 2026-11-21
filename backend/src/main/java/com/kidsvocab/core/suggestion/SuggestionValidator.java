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
 *   <li>the translation differs from the native word and is not just the native word written in
 *       another alphabet or with slightly different letters (for example a German noun spelled in Cyrillic);
 *       true look-alikes such as "taxi" are dropped as well, they are too easy to be worth a slot;</li>
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
            "de", LATIN.or(cp -> "äöüß".indexOf(cp) >= 0),
            "en", LATIN,
            "et", LATIN.or(cp -> "õäöüšž".indexOf(cp) >= 0),
            "ru", cp -> (cp >= 0x0430 && cp <= 0x044F) || cp == 0x0451);   // a..ya, yo

    /** Latin spelling of the Russian letters a..ya (0x0430..0x044F). */
    private static final String[] CYRILLIC_LATIN = {
            "a", "b", "v", "g", "d", "e", "zh", "z", "i", "i", "k", "l", "m", "n", "o", "p",
            "r", "s", "t", "u", "f", "h", "ts", "ch", "sh", "shch", "", "i", "", "e", "ju", "ja"};
    private static final String ACCENTED = "äöüõßšž";
    private static final String[] ACCENTED_PLAIN = {"a", "o", "u", "o", "ss", "sh", "zh"};

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
                    || source.equals(target) || skeleton(source).equals(skeleton(target))
                    || containsStopWord(source) || containsStopWord(target)
                    || !seenSources.add(source) || !seenTargets.add(target)) {
                continue;
            }
            String english = word.english() == null ? null : AnswerNormalizer.normalize(word.english(), Locale.ENGLISH);
            valid.add(new ValidatedWord(word.source().strip(), word.target().strip(), source, target,
                    topicOf(word.topic()), english == null || english.isEmpty() ? null : english));
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

    /**
     * A rough sound shape of a word: Cyrillic is spelled in Latin letters, accents are dropped, letters that
     * sound alike are merged and doubled letters are collapsed, so "maus" in Cyrillic, "Maus" and "mause"
     * are far apart but "maus" in Cyrillic and "Maus" are the same.
     */
    static String skeleton(String normalized) {
        StringBuilder latin = new StringBuilder();
        normalized.codePoints().forEach(cp -> latin.append(latinOf(cp)));
        String s = latin.toString().replaceAll("[ '-]", "")
                .replace("sch", "sh").replace("ck", "k").replace("ph", "f").replace("kh", "h").replace("x", "ks")
                .replace('c', 'k').replace('q', 'k').replace('w', 'v').replace('y', 'i')
                .replace('z', 's').replace("ts", "s");
        return s.replaceAll("(.)\\1+", "$1");
    }

    private static String latinOf(int cp) {
        if (cp == 0x0451) {   // yo
            return "e";
        }
        if (cp >= 0x0430 && cp <= 0x044F) {
            return CYRILLIC_LATIN[cp - 0x0430];
        }
        int accented = ACCENTED.indexOf(cp);
        return accented >= 0 ? ACCENTED_PLAIN[accented] : Character.toString(cp);
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
