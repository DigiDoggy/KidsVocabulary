package com.kidsvocab.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kidsvocab.core.suggestion.SuggestedWord;
import com.kidsvocab.domain.child.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleWordSuggestionProviderTest {

    private static final String URL = "https://ai.test/v1/chat/completions";
    private static final SuggestionRequest REQUEST = new SuggestionRequest(null, Level.A0, null,
            List.of("animals", "food"), 2, "ru", "en", Set.of());

    private MockRestServiceServer server;
    private OpenAiCompatibleWordSuggestionProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.test/v1")
                .defaultHeader("Authorization", "Bearer secret");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiCompatibleWordSuggestionProvider(builder.build(), new ObjectMapper(), "test-model");
    }

    @Test
    void usesJsonModeAndParsesAnObjectAnswer() {
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Native language: Russian"),
                        org.hamcrest.Matchers.containsString("Child: school age, level A0"),
                        org.hamcrest.Matchers.containsString("Topics to choose from: animals, food"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Do not suggest")))))
                .andRespond(withSuccess(answer("{\\\"words\\\": [{\\\"source\\\": \\\"x\\\", \\\"target\\\": \\\"cat\\\"}]}"),
                        MediaType.APPLICATION_JSON));

        assertThat(provider.suggest(REQUEST)).containsExactly(new SuggestedWord("x", "cat", null, "cat"));
        assertThat(provider.id()).isEqualTo("openai-compatible");
    }

    @Test
    void acceptsBareArraysAndCodeFences() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                answer("```json\\n[{\\\"source\\\": \\\"x\\\", \\\"target\\\": \\\"dog\\\", \\\"topic\\\": 5}]\\n```"),
                MediaType.APPLICATION_JSON));

        assertThat(provider.suggest(REQUEST)).containsExactly(new SuggestedWord("x", "dog", null, "dog"));
    }

    @Test
    void rejectsMissingContentOrWordList() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"choices\": [{\"message\": {\"content\": null}}]}", MediaType.APPLICATION_JSON));
        assertInvalid();

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("{\\\"items\\\": []}"), MediaType.APPLICATION_JSON));
        assertInvalid();

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("   "), MediaType.APPLICATION_JSON));
        assertInvalid();
    }

    private void assertInvalid() {
        assertThatThrownBy(() -> provider.suggest(REQUEST))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.kind()).isEqualTo(ProviderException.Kind.INVALID_RESPONSE));
    }

    private static String answer(String escapedContent) {
        return "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"" + escapedContent + "\"}}]}";
    }
}
