package com.kidsvocab.ai;

import com.kidsvocab.core.suggestion.SuggestedWord;

import java.util.List;

/**
 * An external word generator (an LLM API). Implementations only talk to the API and parse its
 * answer; validation, storage and fallback happen in the caller.
 */
public interface WordSuggestionProvider {

    /** Short id used in logs, e.g. {@code gemini}. */
    String id();

    /**
     * @throws ProviderException if the API is unreachable, rate-limited or answers with something unusable
     */
    List<SuggestedWord> suggest(SuggestionRequest request);
}
