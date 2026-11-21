package com.kidsvocab.web.week;

import com.fasterxml.jackson.databind.JsonNode;
import com.kidsvocab.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * German as a target language: the parent types the words, the child trains them.
 * The spelling is checked case-insensitively, but the correct answer keeps its capital letter.
 */
class GermanTargetIntegrationTest extends IntegrationTest {

    private Session parent;
    private long childId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2027-01-13T10:00:00Z"));
        parent = register();
        childId = read(call(post("/api/v1/children"), Map.of("name", "Mia", "level", "A1",
                "nativeLang", "et", "targetLang", "de", "defaultWeekSize", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetLang").value("de"))).get("id").asLong();
    }

    @Test
    void germanIsALanguageOfTheApp() throws Exception {
        mvc.perform(get("/api/v1/info")).andExpect(jsonPath("$.languages[?(@.code == 'de')].name").value("German"));
    }

    @Test
    void parentTypesGermanWordsAndTheChildSpellsThem() throws Exception {
        long weekId = read(call(post("/api/v1/children/" + childId + "/weeks"), Map.of())).get("id").asLong();

        addWord(weekId, "tänav", "Straße", List.of());
        JsonNode week = read(addWord(weekId, "koer", "Hund", List.of()));
        assertThat(week.get("missing").asInt()).isZero();
        assertThat(week.get("words").findValuesAsText("answer")).containsExactlyInAnyOrder("Straße", "Hund");

        call(post("/api/v1/weeks/" + weekId + "/launch")).andExpect(jsonPath("$.status").value("ACTIVE"));
        Session child = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + childId), parent)));
        long sessionId = read(as(child, post("/api/v1/weeks/" + weekId + "/sessions"))).get("id").asLong();

        Map<String, String> answers = new HashMap<>();
        week.get("words").forEach(w -> answers.put(w.get("prompt").asText(), w.get("answer").asText()));

        // round 1: "strasse" is not the German spelling of "Straße"; "hund" is right in any letter case
        for (int i = 0; i < 2; i++) {
            JsonNode item = read(as(child, get("/api/v1/sessions/" + sessionId))).get("current");
            boolean street = item.get("prompt").asText().equals("tänav");
            JsonNode result = read(as(child, post("/api/v1/sessions/" + sessionId + "/answers"), Map.of(
                    "weekWordId", item.get("wordId").asLong(), "round", 1,
                    "answer", street ? "strasse" : "hund")));
            if (street) {
                assertThat(result.get("verdict").asText()).isEqualTo("WRONG");
                assertThat(result.get("correctAnswer").asText()).isEqualTo("Straße");
            } else {
                assertThat(result.get("verdict").asText()).isEqualTo("CORRECT");
            }
        }

        // round 2: the street, now with ß; a hint shows the length first, letters keep their case
        JsonNode item = read(as(child, get("/api/v1/sessions/" + sessionId))).get("current");
        assertThat(item.get("round").asInt()).isEqualTo(2);
        Map<String, Object> hint = Map.of("weekWordId", item.get("wordId").asLong(), "round", 2);
        assertThat(read(as(child, post("/api/v1/sessions/" + sessionId + "/hint"), hint)).get("hintMask").asText())
                .isEqualTo("______");
        JsonNode done = read(as(child, post("/api/v1/sessions/" + sessionId + "/answers"), Map.of(
                "weekWordId", item.get("wordId").asLong(), "round", 2, "answer", "STRAẞE")));
        assertThat(done.get("verdict").asText()).isEqualTo("CORRECT");
        assertThat(done.get("sessionFinished").asBoolean()).isTrue();
        assertThat(answers).containsEntry("koer", "Hund");
    }

    private ResultActions addWord(long weekId, String source, String target, List<String> extra) throws Exception {
        return call(post("/api/v1/weeks/" + weekId + "/words"),
                Map.of("source", source, "target", target, "extraAnswers", extra))
                .andExpect(status().isOk());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return as(parent, request, body);
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(withAuth(request, parent));
    }

    private ResultActions as(Session who, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(withAuth(request, who));
    }

    private ResultActions as(Session who, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(withAuth(request, who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
