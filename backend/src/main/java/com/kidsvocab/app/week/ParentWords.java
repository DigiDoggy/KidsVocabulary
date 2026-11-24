package com.kidsvocab.app.week;

import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.core.text.AnswerNormalizer;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.dictionary.Concept;
import com.kidsvocab.domain.dictionary.ConceptRepository;
import com.kidsvocab.domain.dictionary.DictionaryQueries;
import com.kidsvocab.domain.dictionary.WordForm;
import com.kidsvocab.domain.dictionary.WordFormRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Words the parent types in: a known pair is reused, anything else becomes the parent's own concept. */
@Component
public class ParentWords {

    private final ConceptRepository concepts;
    private final WordFormRepository forms;
    private final DictionaryQueries dictionary;
    private final Clock clock;

    public ParentWords(ConceptRepository concepts, WordFormRepository forms, DictionaryQueries dictionary,
                       Clock clock) {
        this.concepts = concepts;
        this.forms = forms;
        this.dictionary = dictionary;
        this.clock = clock;
    }

    /**
     * Returns the concept for a native word and its translation in the child's languages.
     *
     * @throws AppException VALIDATION if a part is empty or both are the same
     */
    public long conceptFor(Child child, long parentId, String source, String target, List<String> extraAnswers) {
        String nativeNorm = AnswerNormalizer.normalize(source, locale(child.getNativeLang()));
        String targetNorm = AnswerNormalizer.normalize(target, locale(child.getTargetLang()));
        if (nativeNorm.isEmpty() || targetNorm.isEmpty()) {
            throw new AppException(Code.VALIDATION, "Both the word and its translation are required");
        }
        if (nativeNorm.equals(targetNorm)) {
            throw new AppException(Code.VALIDATION, "The translation must differ from the word");
        }
        return dictionary.findPair(child.getNativeLang(), nativeNorm, child.getTargetLang(), targetNorm, parentId)
                .orElseGet(() -> create(child, source, target, extraAnswers));
    }

    private long create(Child child, String source, String target, List<String> extraAnswers) {
        Locale nativeLocale = locale(child.getNativeLang());
        Locale targetLocale = locale(child.getTargetLang());
        String targetText = target.strip();
        Concept concept = concepts.save(new Concept(null, Concept.difficultyOf(targetText), Concept.Origin.MANUAL,
                child.getParentId(), clock.instant()));
        forms.save(new WordForm(concept.getId(), child.getNativeLang(), source.strip(),
                AnswerNormalizer.normalize(source, nativeLocale), true));
        String primary = AnswerNormalizer.normalize(target, targetLocale);
        forms.save(new WordForm(concept.getId(), child.getTargetLang(), targetText, primary, true));
        Set<String> seen = new HashSet<>(Set.of(primary));
        for (String extra : extraAnswers == null ? List.<String>of() : extraAnswers) {
            String normalized = AnswerNormalizer.normalize(extra, targetLocale);
            if (!normalized.isEmpty() && seen.add(normalized)) {
                forms.save(new WordForm(concept.getId(), child.getTargetLang(), extra.strip(), normalized, false));
            }
        }
        forms.flush();
        return concept.getId();
    }

    private static Locale locale(String lang) {
        return Locale.forLanguageTag(lang);
    }
}
