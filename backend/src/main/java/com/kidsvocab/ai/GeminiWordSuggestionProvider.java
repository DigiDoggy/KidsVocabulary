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

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("words", Map.of(
                    "type", "ARRAY",
                    "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "source", Map.of("type", "STRING"),
                                    "target", Map.of("type", "STRING"),
                                    "topic", Map.of("type", "STRING"),
                                    "english", Map.of("type", "STRING"),
                                    "emoji", Map.of("type", "STRING")),
                            "required", List.of("source", "target")))),
            "required", List.of("words"));

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
    protected JsonNode send(SuggestionRequest request) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SuggestionPrompt.system()))),
                "contents", List.of(Map.of("role", "user",
                        "parts", List.of(Map.of("text", SuggestionPrompt.user(request))))),
                "generationConfig", Map.of(
                        "temperature", 0.9,
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA));
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
