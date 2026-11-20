package com.kidsvocab.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Optional;

/** Creates the configured provider, if any. Without one the app uses the local dictionary only. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta";
    /** The lite alias answers in 1–3 s; the full Flash model took 30–60 s on the free tier. */
    static final String GEMINI_MODEL = "gemini-flash-lite-latest";
    static final String GROQ_URL = "https://api.groq.com/openai/v1";
    static final String GROQ_MODEL = "openai/gpt-oss-120b";

    /** Null (no bean) when no provider is configured; consumers use {@code ObjectProvider}. */
    @Bean
    WordSuggestionProvider wordSuggestionProvider(AiProperties properties, RestClient.Builder builder,
                                                  ObjectMapper json) {
        return create(properties, builder, json).orElse(null);
    }

    static Optional<WordSuggestionProvider> create(AiProperties p, RestClient.Builder builder, ObjectMapper json) {
        String provider = p.provider() == null ? "none" : p.provider().strip().toLowerCase();
        if (provider.isEmpty() || provider.equals("none")) {
            return Optional.empty();
        }
        if (p.apiKey() == null || p.apiKey().isBlank()) {
            throw new IllegalStateException("AI_API_KEY is required when AI_PROVIDER=" + provider);
        }
        RestClient.Builder http = builder.clone().requestFactory(requestFactory(p));
        return switch (provider) {
            case "gemini" -> Optional.of(new GeminiWordSuggestionProvider(
                    http.baseUrl(orDefault(p.baseUrl(), GEMINI_URL))
                            .defaultHeader("x-goog-api-key", p.apiKey()).build(),
                    json, orDefault(p.model(), GEMINI_MODEL)));
            case "openai-compatible" -> Optional.of(new OpenAiCompatibleWordSuggestionProvider(
                    http.baseUrl(orDefault(p.baseUrl(), GROQ_URL))
                            .defaultHeader("Authorization", "Bearer " + p.apiKey()).build(),
                    json, orDefault(p.model(), GROQ_MODEL)));
            default -> throw new IllegalStateException(
                    "Unknown AI_PROVIDER '" + provider + "' (expected none, gemini or openai-compatible)");
        };
    }

    private static JdkClientHttpRequestFactory requestFactory(AiProperties p) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(p.timeout()).build());
        factory.setReadTimeout(p.timeout());
        return factory;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
