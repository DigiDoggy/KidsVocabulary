package com.kidsvocab.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Any API that implements OpenAI's {@code /chat/completions} (Groq, OpenRouter, Mistral, ...),
 * in JSON mode (the schema is described in the prompt, not enforced).
 */
public class OpenAiCompatibleWordSuggestionProvider extends HttpWordSuggestionProvider {

    private final String model;

    public OpenAiCompatibleWordSuggestionProvider(RestClient http, ObjectMapper json, String model) {
        super(http, json);
        this.model = model;
    }

    @Override
    public String id() {
        return "openai-compatible";
    }

    @Override
    protected JsonNode send(String system, String user, Map<String, Object> schema) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.9,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        return http.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    @Override
    protected String extractText(JsonNode response) {
        JsonNode text = response == null ? null
                : response.path("choices").path(0).path("message").get("content");
        return text == null || text.isNull() ? null : text.asText();
    }
}
