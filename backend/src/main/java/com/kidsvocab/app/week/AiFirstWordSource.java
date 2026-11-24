package com.kidsvocab.app.week;

import com.kidsvocab.ai.AiProperties;
import com.kidsvocab.ai.AiRequestLog;
import com.kidsvocab.ai.ProviderException;
import com.kidsvocab.ai.SuggestionRequest;
import com.kidsvocab.ai.WordSuggestionProvider;
import com.kidsvocab.core.suggestion.SuggestedWord;
import com.kidsvocab.core.suggestion.SuggestionValidator;
import com.kidsvocab.core.suggestion.ValidatedWord;
import com.kidsvocab.domain.dictionary.Concept;
import com.kidsvocab.domain.dictionary.ConceptRepository;
import com.kidsvocab.domain.dictionary.DictionaryQueries;
import com.kidsvocab.domain.dictionary.WordForm;
import com.kidsvocab.domain.dictionary.WordFormRepository;
import com.kidsvocab.domain.study.WordSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Suggestions from the configured AI provider, topped up from the local dictionary.
 * <ol>
 *   <li>Ask the provider for a few more words than needed; validate the answer.</li>
 *   <li>Store new words in the shared dictionary (reusing known pairs) so they can be reused later.</li>
 *   <li>If too few words survive, ask once more with the extended exclusion list.</li>
 *   <li>Whatever is still missing comes from {@link DictionaryWordSource}.</li>
 * </ol>
 * After a network error or an exhausted quota the provider is skipped for {@code app.ai.cooldown}.
 */
@Component
@Primary
public class AiFirstWordSource implements WordSuggestionSource {

    static final int MAX_CALLS = 2;
    private static final Logger log = LoggerFactory.getLogger(AiFirstWordSource.class);

    private final ObjectProvider<WordSuggestionProvider> provider;
    private final DictionaryWordSource dictionarySource;
    private final DictionaryQueries dictionary;
    private final ConceptRepository concepts;
    private final WordFormRepository forms;
    private final AiRequestLog requestLog;
    private final AiProperties properties;
    private final Clock clock;
    private final Set<String> stopWords;
    private final AtomicReference<Instant> skipUntil = new AtomicReference<>(Instant.MIN);

    public AiFirstWordSource(ObjectProvider<WordSuggestionProvider> provider, DictionaryWordSource dictionarySource,
                             DictionaryQueries dictionary, ConceptRepository concepts, WordFormRepository forms,
                             AiRequestLog requestLog, AiProperties properties, Clock clock) {
        this.provider = provider;
        this.dictionarySource = dictionarySource;
        this.dictionary = dictionary;
        this.concepts = concepts;
        this.forms = forms;
        this.requestLog = requestLog;
        this.properties = properties;
        this.clock = clock;
        this.stopWords = loadStopWords();
    }

    @Override
    public List<Suggestion> suggest(Request request) {
        List<Suggestion> result = new ArrayList<>();
        Set<Long> excludeConcepts = new HashSet<>(request.excludeConcepts());
        Set<String> excludePrompts = new HashSet<>(request.excludePrompts());
        Set<String> excludeAnswers = new HashSet<>(request.excludeAnswers());

        WordSuggestionProvider ai = provider.getIfAvailable();
        if (ai != null && request.count() > 0 && clock.instant().isAfter(skipUntil.get())) {
            fromAi(ai, request, result, excludeConcepts, excludePrompts, excludeAnswers);
        }
        int missing = request.count() - result.size();
        if (missing > 0) {
            result.addAll(dictionarySource.suggest(new Request(request.parentId(), request.childAge(),
                    request.level(), request.nativeLang(), request.targetLang(), request.topic(), missing,
                    excludeConcepts, excludePrompts, excludeAnswers)));
        }
        return result;
    }

    private void fromAi(WordSuggestionProvider ai, Request request, List<Suggestion> result,
                        Set<Long> excludeConcepts, Set<String> excludePrompts, Set<String> excludeAnswers) {
        Set<String> avoidTargets = new HashSet<>(excludeAnswers);
        avoidTargets.addAll(dictionary.primaryWords(excludeConcepts, request.targetLang()));
        List<String> topics = concepts.findSharedTopics();
        SuggestionValidator validator = new SuggestionValidator(stopWords, Set.copyOf(topics));

        for (int call = 0; call < MAX_CALLS && result.size() < request.count(); call++) {
            int wanted = request.count() - result.size();
            SuggestionRequest aiRequest = new SuggestionRequest(request.childAge(), request.level(), request.topic(),
                    topics, wanted + Math.max(2, wanted / 3), request.nativeLang(), request.targetLang(), avoidTargets);
            long started = System.nanoTime();
            List<SuggestedWord> words;
            try {
                words = ai.suggest(aiRequest);
            } catch (ProviderException e) {
                handleFailure(ai, e, millisSince(started));
                if (e.kind() != ProviderException.Kind.INVALID_RESPONSE) {
                    return;
                }
                continue;
            }
            List<ValidatedWord> valid = validator.validate(words, request.nativeLang(), request.targetLang(),
                    new SuggestionValidator.Exclusions(excludePrompts, avoidTargets));
            int added = 0;
            for (ValidatedWord word : valid) {
                avoidTargets.add(word.targetNormalized());
                if (result.size() >= request.count()) {
                    break;
                }
                Optional<Long> conceptId = store(word, request, excludeConcepts);
                if (conceptId.isPresent()) {
                    result.add(new Suggestion(conceptId.get(), WordSource.AI));
                    excludeConcepts.add(conceptId.get());
                    excludePrompts.add(word.sourceNormalized());
                    excludeAnswers.add(word.targetNormalized());
                    added++;
                }
            }
            requestLog.record(ai.id(), AiRequestLog.Status.OK, words.size(), added, millisSince(started), null);
        }
    }

    /**
     * Returns the concept for a validated word: a known pair is reused; a native word that already names a
     * shared concept without a word in the target language completes that concept (this is how a target
     * language without seed words, such as German, builds its dictionary); otherwise a new shared AI concept
     * is created, unless the native word already means something else.
     */
    private Optional<Long> store(ValidatedWord word, Request request, Set<Long> excludeConcepts) {
        Optional<Long> known = dictionary.findPair(request.nativeLang(), word.sourceNormalized(),
                request.targetLang(), word.targetNormalized(), request.parentId());
        if (known.isPresent()) {
            return known.filter(id -> !excludeConcepts.contains(id));
        }
        if (dictionary.sharedPrimaryExists(request.nativeLang(), word.sourceNormalized())) {
            Optional<Long> incomplete = dictionary.sharedConceptWithoutLanguage(
                    request.nativeLang(), word.sourceNormalized(), request.targetLang(), word.englishNormalized());
            incomplete.ifPresent(id -> {
                forms.save(new WordForm(id, request.targetLang(), word.target(), word.targetNormalized(), true));
                forms.flush();
                concepts.findById(id).filter(c -> c.getEmoji() == null && word.emoji() != null)
                        .ifPresent(c -> c.changeEmoji(word.emoji()));
            });
            // otherwise the native word already means something else in this language
            return incomplete.filter(id -> !excludeConcepts.contains(id));
        }
        Concept concept = concepts.save(new Concept(word.topic(), Concept.difficultyOf(word.target()),
                Concept.Origin.AI, null, clock.instant()));
        concept.changeEmoji(word.emoji());
        forms.save(new WordForm(concept.getId(), request.nativeLang(), word.source(), word.sourceNormalized(), true));
        forms.save(new WordForm(concept.getId(), request.targetLang(), word.target(), word.targetNormalized(), true));
        forms.flush();
        return Optional.of(concept.getId());
    }

    private void handleFailure(WordSuggestionProvider ai, ProviderException e, long latencyMs) {
        AiRequestLog.Status status = switch (e.kind()) {
            case RATE_LIMITED -> AiRequestLog.Status.RATE_LIMIT;
            case INVALID_RESPONSE -> AiRequestLog.Status.INVALID;
            case UNAVAILABLE -> AiRequestLog.Status.ERROR;
        };
        requestLog.record(ai.id(), status, null, null, latencyMs, e.getMessage());
        if (e.kind() != ProviderException.Kind.INVALID_RESPONSE) {
            skipUntil.set(clock.instant().plus(properties.cooldown()));
            log.warn("AI provider {} failed ({}); using the local dictionary for {}", ai.id(), e.getMessage(),
                    properties.cooldown());
        }
    }

    /** Ends a running cooldown (used by tests). */
    void resetCooldown() {
        skipUntil.set(Instant.MIN);
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static Set<String> loadStopWords() {
        try {
            String text = new ClassPathResource("ai/stop-words.txt").getContentAsString(StandardCharsets.UTF_8);
            Set<String> words = new HashSet<>();
            text.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(words::add);
            return words;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
