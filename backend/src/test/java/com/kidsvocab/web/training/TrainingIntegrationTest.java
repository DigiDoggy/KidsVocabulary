package com.kidsvocab.web.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.kidsvocab.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Training and progress end to end. Time is pinned to Wednesday 2027-01-13 (Europe/Tallinn);
 * the week of the set runs 2027-01-11 .. 2027-01-17.
 */
class TrainingIntegrationTest extends IntegrationTest {

    private static final Instant WEDNESDAY = Instant.parse("2027-01-13T10:00:00Z");
    private static final List<String[]> WORDS = List.of(
            new String[]{"koer", "dog"}, new String[]{"kass", "cat"}, new String[]{"lehm", "cow"},
            new String[]{"siga", "pig"}, new String[]{"lammas", "sheep"});

    private Session parent;
    private Session child;
    private long childId;
    /** week word id -> correct answer, for the current week */
    private final Map<Long, String> answers = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        clock.set(WEDNESDAY);
        parent = register();
        childId = readId(as(parent, post("/api/v1/children"), Map.of("name", "Mia", "level", "A1",
                "nativeLang", "et", "targetLang", "en")));
        child = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + childId), parent)));
    }

    // --- first pass ------------------------------------------------------------------------------

    @Test
    void perfectFirstPassClosesTheWeekWithFullPoints() throws Exception {
        long weekId = launchWeek(0, 3);

        JsonNode s = read(as(child, post("/api/v1/weeks/" + weekId + "/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("FIRST_PASS"))
                .andExpect(jsonPath("$.counts").value(true))
                .andExpect(jsonPath("$.current.round").value(1))
                .andExpect(jsonPath("$.current.roundSize").value(3))
                .andExpect(jsonPath("$.current.hintMask").exists()));
        long sessionId = s.get("id").asLong();

        JsonNode last = null;
        for (int i = 0; i < 3; i++) {
            last = answerCurrent(sessionId, true);
        }
        assertThat(last.get("sessionFinished").asBoolean()).isTrue();
        assertThat(last.get("next").isNull()).isTrue();

        as(parent, get("/api/v1/weeks/" + weekId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.points").value(6.0))
                .andExpect(jsonPath("$.maxPoints").value(6.0));
        as(child, get("/api/v1/me/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.child.name").value("Mia"))
                .andExpect(jsonPath("$.week.status").value("COMPLETED"))
                .andExpect(jsonPath("$.week.points").value(6.0))
                .andExpect(jsonPath("$.month.weeksClosed").value(1))
                .andExpect(jsonPath("$.month.points").value(6.0));
    }

    @Test
    void mistakesAndHintsLowerTheScore() throws Exception {
        long weekId = launchWeek(0, 3);
        long sessionId = startSession(weekId);

        // round 1: first word right, second word wrong, third word right after a hint
        answerCurrent(sessionId, true);
        JsonNode wrong = answerCurrent(sessionId, false);
        assertThat(wrong.get("verdict").asText()).isEqualTo("WRONG");
        assertThat(wrong.get("correctAnswer").asText()).isNotBlank();
        assertThat(wrong.get("showCorrectAnswerFor").asInt()).isEqualTo(5);
        JsonNode third = current(sessionId);
        as(child, post("/api/v1/sessions/" + sessionId + "/hint"), Map.of(
                        "weekWordId", third.get("wordId").asLong(), "round", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintMask").value(org.hamcrest.Matchers.matchesPattern("^[a-z]_+$")));
        JsonNode endOfRound = answerCurrent(sessionId, true);
        assertThat(endOfRound.get("roundFinished").asBoolean()).isTrue();
        assertThat(endOfRound.get("next").get("round").asInt()).isEqualTo(2);

        // round 2: the missed word, now right
        JsonNode done = answerCurrent(sessionId, true);
        assertThat(done.get("sessionFinished").asBoolean()).isTrue();
        assertThat(done.get("points").asDouble()).isEqualTo(2.0 + 1.0 + 1.0);

        JsonNode stats = read(as(parent, get("/api/v1/weeks/" + weekId + "/stats")));
        assertThat(stats).hasSize(3);
        int hints = 0;
        int wrongRounds = 0;
        for (JsonNode w : stats) {
            hints += w.get("hintUsed").asBoolean() ? 1 : 0;
            wrongRounds += w.get("wrongRounds").asInt();
            assertThat(w.get("outcome").asText()).isEqualTo("LEARNED");
        }
        assertThat(hints).isEqualTo(1);
        assertThat(wrongRounds).isEqualTo(1);
    }

    @Test
    void trainingCanBeInterruptedAndResumed() throws Exception {
        long weekId = launchWeek(0, 3);
        long sessionId = startSession(weekId);
        answerCurrent(sessionId, true);
        JsonNode second = current(sessionId);
        as(child, post("/api/v1/sessions/" + sessionId + "/hint"),
                Map.of("weekWordId", second.get("wordId").asLong(), "round", 1));

        JsonNode resumed = read(as(child, post("/api/v1/weeks/" + weekId + "/sessions")));

        assertThat(resumed.get("id").asLong()).isEqualTo(sessionId);
        assertThat(resumed.get("current").get("wordId").asLong()).isEqualTo(second.get("wordId").asLong());
        assertThat(resumed.get("current").get("position").asInt()).isEqualTo(2);
        assertThat(resumed.get("current").get("hintMask").asText()).matches("^[a-z]_+$");
        as(child, get("/api/v1/me/home"))
                .andExpect(jsonPath("$.week.firstPassOpen").value(true))
                .andExpect(jsonPath("$.week.points").value(2.0));
    }

    @Test
    void repeatedAndInvalidRequestsAreHandled() throws Exception {
        long weekId = launchWeek(0, 1);
        long sessionId = startSession(weekId);
        JsonNode item = current(sessionId);
        long wordId = item.get("wordId").asLong();

        as(child, post("/api/v1/sessions/" + sessionId + "/answers"),
                Map.of("weekWordId", wordId, "round", 1, "answer", "   "))
                .andExpect(jsonPath("$.verdict").value("IGNORED"));
        as(child, post("/api/v1/sessions/" + sessionId + "/answers"),
                Map.of("weekWordId", wordId + 999, "round", 1, "answer", "x"))
                .andExpect(status().isConflict());

        Map<String, Object> right = Map.of("weekWordId", wordId, "round", 1, "answer", answers.get(wordId));
        as(child, post("/api/v1/sessions/" + sessionId + "/answers"), right)
                .andExpect(jsonPath("$.sessionFinished").value(true));
        // a network retry of the final answer gets the same result, not an error
        as(child, post("/api/v1/sessions/" + sessionId + "/answers"), right)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("CORRECT"))
                .andExpect(jsonPath("$.sessionFinished").value(true));
        assertThat(jdbc.queryForObject("select count(*) from training_event where session_id = ?",
                Integer.class, sessionId)).isEqualTo(1);

        as(child, post("/api/v1/sessions/" + sessionId + "/answers"),
                Map.of("weekWordId", wordId, "round", 2, "answer", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
    }

    @Test
    void afterTheFirstPassTrainingIsPracticeOnly() throws Exception {
        long weekId = launchWeek(0, 2);
        long firstPass = startSession(weekId);
        answerCurrent(firstPass, true);
        answerCurrent(firstPass, true);

        JsonNode practice = read(as(child, post("/api/v1/weeks/" + weekId + "/sessions"))
                .andExpect(jsonPath("$.kind").value("PRACTICE"))
                .andExpect(jsonPath("$.counts").value(false)));
        long practiceId = practice.get("id").asLong();
        assertThat(practiceId).isNotEqualTo(firstPass);
        answerCurrent(practiceId, false);

        as(parent, get("/api/v1/weeks/" + weekId)).andExpect(jsonPath("$.points").value(4.0));
        assertThat(read(as(child, post("/api/v1/weeks/" + weekId + "/sessions"))).get("id").asLong())
                .isEqualTo(practiceId);
    }

    @Test
    void weekEndKeepsSolvedWordsAndZeroesTheRest() throws Exception {
        long weekId = launchWeek(0, 3);
        long sessionId = startSession(weekId);
        answerCurrent(sessionId, true);
        answerCurrent(sessionId, false);
        JsonNode pending = current(sessionId);

        clock.set(Instant.parse("2027-01-17T22:00:00Z"));   // Monday 00:00 in Tallinn
        as(child, post("/api/v1/sessions/" + sessionId + "/answers"), Map.of(
                        "weekWordId", pending.get("wordId").asLong(), "round", 1,
                        "answer", answers.get(pending.get("wordId").asLong())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));

        JsonNode week = read(as(parent, get("/api/v1/weeks/" + weekId))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.points").value(2.0)));
        List<String> outcomes = new java.util.ArrayList<>();
        week.get("words").forEach(w -> outcomes.add(w.get("outcome").asText()));
        assertThat(outcomes).containsExactlyInAnyOrder("LEARNED", "UNLEARNED", "UNLEARNED");

        as(child, post("/api/v1/weeks/" + weekId + "/sessions")).andExpect(jsonPath("$.kind").value("PRACTICE"));
    }

    // --- months ----------------------------------------------------------------------------------

    @Test
    void perfectMonthEarnsTheReward() throws Exception {
        as(parent, put("/api/v1/children/" + childId + "/months/current/reward"), Map.of("reward", "Zoo trip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reward").value("Zoo trip"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        for (int i = 0; i < 4; i++) {
            long weekId = launchWeek(i, 1);
            long sessionId = startSession(weekId);
            answerCurrent(sessionId, true);
            clock.advance(Duration.ofDays(7));
        }

        JsonNode months = read(as(parent, get("/api/v1/children/" + childId + "/months")));
        assertThat(months).hasSize(2);
        JsonNode first = months.get(1);
        assertThat(first.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(first.get("result").asText()).isEqualTo("REWARD");

        as(parent, get("/api/v1/months/" + first.get("id").asLong() + "/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(100.0))
                .andExpect(jsonPath("$.result").value("REWARD"))
                .andExpect(jsonPath("$.reward").value("Zoo trip"))
                .andExpect(jsonPath("$.weeks.length()").value(4))
                .andExpect(jsonPath("$.hardWords.length()").value(0));
        as(parent, get("/api/v1/months/" + months.get(0).get("id").asLong() + "/report"))
                .andExpect(status().isConflict());
    }

    @Test
    void weakMonthListsHardAndUnlearnedWords() throws Exception {
        // week 1: right first time; week 2: hint, then right in round 2 (0.5, hard); weeks 3-4: not trained
        long w1 = launchWeek(0, 1);
        answerCurrent(startSession(w1), true);
        clock.advance(Duration.ofDays(7));

        long w2 = launchWeek(1, 1);
        long s2 = startSession(w2);
        JsonNode item = current(s2);
        as(child, post("/api/v1/sessions/" + s2 + "/hint"), Map.of("weekWordId", item.get("wordId").asLong(), "round", 1));
        answerCurrent(s2, false);
        answerCurrent(s2, true);
        clock.advance(Duration.ofDays(7));

        launchWeek(2, 1);
        clock.advance(Duration.ofDays(7));
        launchWeek(3, 1);
        clock.advance(Duration.ofDays(7));

        long monthId = read(as(parent, get("/api/v1/children/" + childId + "/months"))).get(1).get("id").asLong();
        JsonNode report = read(as(parent, get("/api/v1/months/" + monthId + "/report"))
                .andExpect(jsonPath("$.totalPoints").value(2.5))
                .andExpect(jsonPath("$.maxPoints").value(8.0))
                .andExpect(jsonPath("$.percent").value(31.25))
                .andExpect(jsonPath("$.result").value("REPEAT"))
                .andExpect(jsonPath("$.reward").doesNotExist()));
        assertThat(report.get("hardWords").get(0).get("answer").asText()).isEqualTo("cat");
        List<String> unlearned = new java.util.ArrayList<>();
        report.get("unlearnedWords").forEach(w -> unlearned.add(w.get("prompt").asText()));
        assertThat(unlearned).containsExactlyInAnyOrder("lehm", "siga");

        as(parent, get("/api/v1/months/" + monthId))
                .andExpect(jsonPath("$.weeks.length()").value(4))
                .andExpect(jsonPath("$.points").value(2.5));
    }

    // --- access ----------------------------------------------------------------------------------

    @Test
    void sessionsAreVisibleToTheFamilyOnly() throws Exception {
        long weekId = launchWeek(0, 1);
        long sessionId = startSession(weekId);

        Session stranger = register();
        as(stranger, post("/api/v1/weeks/" + weekId + "/sessions")).andExpect(status().isNotFound());
        as(stranger, get("/api/v1/sessions/" + sessionId)).andExpect(status().isNotFound());

        long siblingId = readId(as(parent, post("/api/v1/children"), Map.of("name", "Leo", "level", "A1",
                "nativeLang", "et", "targetLang", "en")));
        Session sibling = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + siblingId), parent)));
        as(sibling, get("/api/v1/sessions/" + sessionId)).andExpect(status().isNotFound());

        as(parent, get("/api/v1/sessions/" + sessionId)).andExpect(status().isOk());
        as(child, get("/api/v1/weeks/" + weekId + "/stats")).andExpect(status().isForbidden());
        as(parent, get("/api/v1/me/home")).andExpect(status().isForbidden());
        as(parent, get("/api/v1/children/" + childId + "/home")).andExpect(status().isOk());
    }

    @Test
    void preparedWeekCannotBeTrainedYet() throws Exception {
        long draft = readId(as(parent, post("/api/v1/children/" + childId + "/weeks"), Map.of("wordCount", 1)));
        as(child, post("/api/v1/weeks/" + draft + "/sessions")).andExpect(status().isConflict());
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** Parent builds a week from {@code count} known words starting at {@code offset}, launches it. */
    private long launchWeek(int offset, int count) throws Exception {
        long weekId = readId(as(parent, post("/api/v1/children/" + childId + "/weeks"), Map.of("wordCount", count))
                .andExpect(status().isCreated()));
        JsonNode week = null;
        for (int i = 0; i < count; i++) {
            String[] pair = WORDS.get(offset + i);
            week = read(as(parent, post("/api/v1/weeks/" + weekId + "/words"),
                    Map.of("source", pair[0], "target", pair[1])).andExpect(status().isOk()));
        }
        answers.clear();
        week.get("words").forEach(w -> answers.put(w.get("id").asLong(), w.get("answer").asText()));
        as(parent, post("/api/v1/weeks/" + weekId + "/launch")).andExpect(jsonPath("$.status").value("ACTIVE"));
        return weekId;
    }

    private long startSession(long weekId) throws Exception {
        return read(as(child, post("/api/v1/weeks/" + weekId + "/sessions")).andExpect(status().isOk()))
                .get("id").asLong();
    }

    private JsonNode current(long sessionId) throws Exception {
        return read(as(child, get("/api/v1/sessions/" + sessionId))).get("current");
    }

    private JsonNode answerCurrent(long sessionId, boolean correct) throws Exception {
        JsonNode item = current(sessionId);
        long wordId = item.get("wordId").asLong();
        return read(as(child, post("/api/v1/sessions/" + sessionId + "/answers"), Map.of(
                "weekWordId", wordId, "round", item.get("round").asInt(),
                "answer", correct ? answers.get(wordId).toUpperCase() + " " : "wrong")).andExpect(status().isOk()));
    }

    private ResultActions as(Session session, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(withAuth(request, session));
    }

    private ResultActions as(Session session, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(withAuth(request, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long readId(ResultActions result) throws Exception {
        return read(result).get("id").asLong();
    }
}
