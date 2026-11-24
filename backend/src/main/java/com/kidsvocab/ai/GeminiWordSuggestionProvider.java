package com.kidsvocab.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini API ({@code models/{model}:generateContent}). Uses structured output, so the model
 * is forced to answer with the expected JSON schema.
 */
public class GeminiWordSuggestionProvider extends HttpWordSuggestionProvider {

    private final String model;

    public GeminiWordSuggestionProvider(RestClient http, ObjectMapper json, String model) {
        super(http, json);
        this.model = model;
    }

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    protected JsonNode send(String system, String user, Map<String, Object> schema) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                "generationConfig", Map.of(
                        "temperature", 0.9,
                        "responseMimeType", "application/json",
                        "responseSchema", schema));
        return http.post()
                .uri("/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    @Override
    protected String extractText(JsonNode response) {
        JsonNode text = response == null ? null
                : response.path("candidates").path(0).path("content").path("parts").path(0).get("text");
        return text == null ? null : text.asText();
    }
}
