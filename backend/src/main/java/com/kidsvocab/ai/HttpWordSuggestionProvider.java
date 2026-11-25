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
import java.util.Map;

/**
 * Shared HTTP handling for JSON-over-HTTP model APIs: one POST, error mapping, and parsing of the
 * model's text answer into words or filler letters.
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
        List<SuggestedWord> words = parseWords(ask(SuggestionPrompt.system(request), SuggestionPrompt.user(request),
                SuggestionPrompt.schema(request)));
        return words.stream().map(w -> completed(w, request)).toList();
    }

    /** Fills in the fields that were not asked for because the request already answers them. */
    private static SuggestedWord completed(SuggestedWord w, SuggestionRequest request) {
        String topic = w.topic() == null && !SuggestionPrompt.asksTopic(request) ? request.topic() : w.topic();
        String english = w.english() == null && !SuggestionPrompt.asksEnglish(request) ? w.target() : w.english();
        return new SuggestedWord(w.source(), w.target(), topic, english, w.emoji());
    }

    @Override
    public final String fillerLetters(FillerRequest request) {
        JsonNode letters = parseJson(ask(FillerPrompt.system(), FillerPrompt.user(request), FillerPrompt.SCHEMA))
                .path("letters");
        if (!letters.isTextual()) {
            throw invalid("the JSON has no letters", null);
        }
        return letters.asText();
    }

    /** Sends one prompt and returns the model's text answer. */
    private String ask(String system, String user, Map<String, Object> schema) {
        JsonNode response;
        try {
            response = send(system, user, schema);
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
        return text;
    }

    /**
     * Performs the HTTP call and returns the raw JSON response.
     *
     * @param schema the expected answer as a JSON schema, for APIs with structured output
     */
    protected abstract JsonNode send(String system, String user, Map<String, Object> schema);

    /** Picks the model's generated text out of the response, or {@code null}. */
    protected abstract String extractText(JsonNode response);

    /** Accepts {@code {"words": [...]}} or a bare array, optionally wrapped in a Markdown code fence. */
    List<SuggestedWord> parseWords(String text) {
        JsonNode root = parseJson(text);
        JsonNode items = root.isArray() ? root : root.path("words");
        if (!items.isArray()) {
            throw invalid("the JSON has no word list", null);
        }
        List<SuggestedWord> words = new ArrayList<>();
        for (JsonNode item : items) {
            words.add(new SuggestedWord(text(item, "source"), text(item, "target"), text(item, "topic"),
                    text(item, "english"), text(item, "emoji")));
        }
        return words;
    }

    /** Parses the model's JSON answer, optionally wrapped in a Markdown code fence. */
    private JsonNode parseJson(String text) {
        String body = text.strip();
        if (body.startsWith("```")) {
            body = body.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            return json.readTree(body);
        } catch (JsonProcessingException e) {
            throw invalid("the model did not return JSON", e);
        }
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private ProviderException invalid(String message, Throwable cause) {
        return new ProviderException(ProviderException.Kind.INVALID_RESPONSE, id() + ": " + message, cause);
    }
}
