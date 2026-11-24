package com.kidsvocab.app.game;

import com.kidsvocab.ai.AiProperties;
import com.kidsvocab.ai.AiRequestLog;
import com.kidsvocab.ai.FillerRequest;
import com.kidsvocab.ai.ProviderException;
import com.kidsvocab.ai.WordSuggestionProvider;
import com.kidsvocab.core.game.WordSearch;
import com.kidsvocab.core.text.Alphabet;
import com.kidsvocab.domain.game.WordSearchGrid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds word-search grids. The free cells get letters from the AI provider, so the grid looks like the
 * language rather than random noise; without a provider, or when it fails, the grid uses random letters.
 * After a network error or an exhausted quota the provider is skipped for {@code app.ai.cooldown}.
 */
@Component
public class WordSearchMaker {

    private static final Logger log = LoggerFactory.getLogger(WordSearchMaker.class);

    private final ObjectProvider<WordSuggestionProvider> provider;
    private final AiRequestLog requestLog;
    private final AiProperties properties;
    private final Clock clock;
    private final SecureRandom seeds = new SecureRandom();
    private final AtomicReference<Instant> skipUntil = new AtomicReference<>(Instant.MIN);

    public WordSearchMaker(ObjectProvider<WordSuggestionProvider> provider, AiRequestLog requestLog,
                           AiProperties properties, Clock clock) {
        this.provider = provider;
        this.requestLog = requestLog;
        this.properties = properties;
        this.clock = clock;
    }

    /** A word with its id (week word or task word) and the text to hide. */
    public record Word(long id, String answer) {
    }

    public WordSearchGrid make(List<Word> words, String lang, boolean diagonal) {
        List<String> answers = words.stream().map(Word::answer).toList();
        WordSearch.Layout layout = WordSearch.layout(answers, diagonal, seeds.nextLong());
        List<String> hidden = layout.placements().stream()
                .map(p -> WordSearch.letters(answers.get(p.word())))
                .toList();
        WordSearch.Grid grid = layout.fill(filler(lang, layout.freeCells(), hidden), Alphabet.of(lang));
        return new WordSearchGrid(grid.size(), grid.rows(),
                grid.placements().stream().map(p -> words.get(p.word()).id()).toList());
    }

    private String filler(String lang, int count, List<String> hidden) {
        WordSuggestionProvider ai = provider.getIfAvailable();
        if (ai == null || count == 0 || !clock.instant().isAfter(skipUntil.get())) {
            return "";
        }
        long started = System.nanoTime();
        try {
            String letters = ai.fillerLetters(new FillerRequest(lang, count, hidden));
            requestLog.record(ai.id(), AiRequestLog.Status.OK, null, null, millisSince(started), null);
            return letters == null ? "" : letters;
        } catch (ProviderException e) {
            AiRequestLog.Status status = switch (e.kind()) {
                case RATE_LIMITED -> AiRequestLog.Status.RATE_LIMIT;
                case INVALID_RESPONSE -> AiRequestLog.Status.INVALID;
                case UNAVAILABLE -> AiRequestLog.Status.ERROR;
            };
            requestLog.record(ai.id(), status, null, null, millisSince(started), e.getMessage());
            if (e.kind() != ProviderException.Kind.INVALID_RESPONSE) {
                skipUntil.set(clock.instant().plus(properties.cooldown()));
            }
            log.warn("AI provider {} gave no filler letters ({}); using random letters", ai.id(), e.getMessage());
            return "";
        }
    }

    /** Ends a running cooldown (used by tests). */
    void resetCooldown() {
        skipUntil.set(Instant.MIN);
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
