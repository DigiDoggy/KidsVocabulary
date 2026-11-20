package com.kidsvocab.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AiConfigTest {

    @Test
    void noProviderMeansDictionaryOnly() {
        assertThat(AiConfig.create(props("none", ""), RestClient.builder(), new ObjectMapper())).isEmpty();
        assertThat(AiConfig.create(props(null, ""), RestClient.builder(), new ObjectMapper())).isEmpty();
        assertThat(AiConfig.create(props(" ", ""), RestClient.builder(), new ObjectMapper())).isEmpty();
    }

    @Test
    void createsTheConfiguredProvider() {
        assertThat(AiConfig.create(props("Gemini", "key"), RestClient.builder(), new ObjectMapper()))
                .get().isInstanceOf(GeminiWordSuggestionProvider.class);
        assertThat(AiConfig.create(props("openai-compatible", "key"), RestClient.builder(), new ObjectMapper()))
                .get().isInstanceOf(OpenAiCompatibleWordSuggestionProvider.class);
    }

    @Test
    void failsFastOnMisconfiguration() {
        assertThatIllegalStateException()
                .isThrownBy(() -> AiConfig.create(props("gemini", " "), RestClient.builder(), new ObjectMapper()))
                .withMessageContaining("AI_API_KEY");
        assertThatIllegalStateException()
                .isThrownBy(() -> AiConfig.create(props("chatbot", "key"), RestClient.builder(), new ObjectMapper()))
                .withMessageContaining("Unknown AI_PROVIDER");
    }

    private static AiProperties props(String provider, String key) {
        return new AiProperties(provider, key, "", "", Duration.ofSeconds(5), Duration.ofMinutes(5));
    }
}
