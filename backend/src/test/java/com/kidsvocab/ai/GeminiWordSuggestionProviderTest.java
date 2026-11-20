package com.kidsvocab.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kidsvocab.core.suggestion.SuggestedWord;
import com.kidsvocab.domain.child.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiWordSuggestionProviderTest {

    private static final String URL = "https://ai.test/models/test-model:generateContent";
    private static final SuggestionRequest REQUEST = new SuggestionRequest(8, Level.A1, "food",
            List.of("animals", "food"), 3, "et", "en", Set.of("apple"));

    private MockRestServiceServer server;
    private GeminiWordSuggestionProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.test")
                .defaultHeader("x-goog-api-key", "secret");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GeminiWordSuggestionProvider(builder.build(), new ObjectMapper(), "test-model");
    }

    @Test
    void sendsAStructuredRequestAndParsesTheWords() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.words.type").value("ARRAY"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(
                        org.hamcrest.Matchers.containsString("Reply with JSON only")))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Native language: Estonian"),
                        org.hamcrest.Matchers.containsString("Number of words: 3"),
                        org.hamcrest.Matchers.containsString("Topic: food"),
                        org.hamcrest.Matchers.containsString("Do not suggest these English words: apple"))))
                .andRespond(withSuccess(geminiAnswer(
                        "{\\\"words\\\": [{\\\"source\\\": \\\"leib\\\", \\\"target\\\": \\\"bread\\\", \\\"topic\\\": \\\"food\\\"}]}"),
                        MediaType.APPLICATION_JSON));

        List<SuggestedWord> words = provider.suggest(REQUEST);

        assertThat(words).containsExactly(new SuggestedWord("leib", "bread", "food"));
        assertThat(provider.id()).isEqualTo("gemini");
        server.verify();
    }

    @Test
    void mapsRateLimitAndServerErrors() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertKind(ProviderException.Kind.RATE_LIMITED);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertKind(ProviderException.Kind.UNAVAILABLE);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withException(new IOException("read timed out")));
        assertKind(ProviderException.Kind.UNAVAILABLE);
    }

    @Test
    void rejectsResponsesWithoutUsableText() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\": []}", MediaType.APPLICATION_JSON));
        assertKind(ProviderException.Kind.INVALID_RESPONSE);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertKind(ProviderException.Kind.INVALID_RESPONSE);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertKind(ProviderException.Kind.INVALID_RESPONSE);
    }

    private void assertKind(ProviderException.Kind kind) {
        assertThatThrownBy(() -> provider.suggest(REQUEST))
                .isInstanceOfSatisfying(ProviderException.class, e -> assertThat(e.kind()).isEqualTo(kind));
    }

    static String geminiAnswer(String escapedText) {
        return "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"" + escapedText + "\"}]}}]}";
    }
}
