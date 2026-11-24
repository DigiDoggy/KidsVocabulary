package com.kidsvocab.ai;

import com.kidsvocab.core.suggestion.SuggestedWord;

import java.util.List;

/**
 * An external word generator (an LLM API): words for weekly sets and letters for word-search grids. Implementations only talk to the API and parse its
 * answer; validation, storage and fallback happen in the caller.
 */
public interface WordSuggestionProvider {

    /** Short id used in logs, e.g. {@code gemini}. */
    String id();

    /**
     * @throws ProviderException if the API is unreachable, rate-limited or answers with something unusable
     */
    List<SuggestedWord> suggest(SuggestionRequest request);

    /**
     * Letters for the free cells of a word-search grid; the caller filters and tops them up.
     *
     * @throws ProviderException if the API fails or the provider cannot do this
     */
    default String fillerLetters(FillerRequest request) {
        throw new ProviderException(ProviderException.Kind.UNAVAILABLE, id() + " cannot suggest filler letters", null);
    }
}
