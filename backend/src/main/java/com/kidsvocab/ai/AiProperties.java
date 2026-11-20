package com.kidsvocab.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param provider  {@code none}, {@code gemini} or {@code openai-compatible}
 * @param apiKey    API key; required unless the provider is {@code none}
 * @param baseUrl   API base URL; empty = the provider's default
 * @param model     model name; empty = the provider's default
 * @param timeout   per-request timeout
 * @param cooldown  how long to skip the provider after it failed or hit its quota
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, String apiKey, String baseUrl, String model, Duration timeout,
                           Duration cooldown) {
}
