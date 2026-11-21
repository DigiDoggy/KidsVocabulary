package com.kidsvocab.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kidsvocab.core.suggestion.SuggestedWord;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared HTTP handling for JSON-over-HTTP model APIs: one POST, error mapping, and parsing of the
 * model's text answer into words.
 */
abstract class HttpWordSuggestionProvider implements WordSuggestionProvider {

    protected final RestClient http;
    protected final ObjectMapper json;

    protected HttpWordSuggestionProvider(RestClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public final List<SuggestedWord> suggest(SuggestionRequest request) {
        JsonNode response;
        try {
            response = send(request);
        } catch (RestClientResponseException e) {
            ProviderException.Kind kind = e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? ProviderException.Kind.RATE_LIMITED : ProviderException.Kind.UNAVAILABLE;
            throw new ProviderException(kind, id() + " answered HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ProviderException(ProviderException.Kind.UNAVAILABLE, id() + " is unreachable", e);
        } catch (RestClientException e) {
            throw new ProviderException(ProviderException.Kind.INVALID_RESPONSE, id() + " sent an unreadable response", e);
        }
        String text = extractText(response);
        if (text == null || text.isBlank()) {
            throw invalid("no text in the response", null);
        }
        return parseWords(text);
    }

    /** Performs the HTTP call and returns the raw JSON response. */
    protected abstract JsonNode send(SuggestionRequest request);

    /** Picks the model's generated text out of the response, or {@code null}. */
    protected abstract String extractText(JsonNode response);

    /** Accepts {@code {"words": [...]}} or a bare array, optionally wrapped in a Markdown code fence. */
    List<SuggestedWord> parseWords(String text) {
        String body = text.strip();
        if (body.startsWith("```")) {
            body = body.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JsonProcessingException e) {
            throw invalid("the model did not return JSON", e);
        }
        JsonNode items = root.isArray() ? root : root.path("words");
        if (!items.isArray()) {
            throw invalid("the JSON has no word list", null);
        }
        List<SuggestedWord> words = new ArrayList<>();
        for (JsonNode item : items) {
            words.add(new SuggestedWord(text(item, "source"), text(item, "target"), text(item, "topic"),
                    text(item, "english")));
        }
        return words;
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private ProviderException invalid(String message, Throwable cause) {
        return new ProviderException(ProviderException.Kind.INVALID_RESPONSE, id() + ": " + message, cause);
    }
}
