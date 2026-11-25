package com.kidsvocab.web.garden;

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
 * The word garden end to end. Time starts on Wednesday 2027-01-13 (Europe/Tallinn); the week of the set
 * runs 2027-01-11 .. 2027-01-17, so a word learned in it wants water from 2027-01-20.
 */
class GardenIntegrationTest extends IntegrationTest {

    private static final Instant WEDNESDAY = Instant.parse("2027-01-13T10:00:00Z");
    private static final Instant NEXT_THURSDAY = Instant.parse("2027-01-21T10:00:00Z");
    private static final List<String[]> WORDS = List.of(
            new String[]{"koer", "dog"}, new String[]{"kass", "cat"}, new String[]{"lehm", "cow"});

    private Session parent;
    private Session child;
    private long childId;
    private final Map<Long, String> answers = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        clock.set(WEDNESDAY);
        parent = register();
        childId = readId(as(parent, post("/api/v1/children"), Map.of("name", "Mia", "level", "A1",
                "nativeLang", "et", "targetLang", "en")));
        child = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + childId), parent)));
    }

    @Test
    void finishedWeekPlantsItsWordsAsSeeds() throws Exception {
        as(child, get("/api/v1/me/garden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantCount").value(0))
                .andExpect(jsonPath("$.beds.length()").value(0));

        long weekId = launchWeek(3);
        long sessionId = startSession(weekId);
        // the third word is missed three times, so it ends as HARD: still planted
        answerCurrent(sessionId, true);
        answerCurrent(sessionId, true);
        for (int i = 0; i < 3; i++) {
            answerCurrent(sessionId, false);
        }
        answerCurrent(sessionId, true);
        // a word without a picture still knows its bed
        jdbc.update("update week_word set emoji = null where week_id = ? and primary_answer = 'cow'", weekId);

        JsonNode garden = read(as(child, get("/api/v1/me/garden")).andExpect(status().isOk()));
        assertThat(garden.get("plantCount").asInt()).isEqualTo(3);
        assertThat(garden.get("dueCount").asInt()).isZero();
        assertThat(garden.get("openReviewId").isNull()).isTrue();
        assertThat(garden.get("beds")).hasSize(1);
        assertThat(garden.get("beds").get(0).get("month").asInt()).isEqualTo(1);
        List<Integer> kinds = new java.util.ArrayList<>();
        garden.get("beds").get(0).get("plants").forEach(p -> kinds.add(p.get("kind").asInt()));
        assertThat(kinds).as("one kind stands together").isSorted();
        for (JsonNode plant : garden.get("beds").get(0).get("plants")) {
            assertThat(plant.get("stage").asText()).isEqualTo("SEED");
            assertThat(plant.get("kind").asInt()).isBetween(0, 7);
            assertThat(plant.get("due").asBoolean()).isFalse();
            assertThat(plant.get("thirsty").asBoolean()).isFalse();
            assertThat(plant.get("prompt").asText()).isIn("koer", "kass", "lehm");
            assertThat(plant.get("answer").asText()).isIn("dog", "cat", "cow");
        }
        as(child, post("/api/v1/me/garden/reviews")).andExpect(status().isConflict());
    }

    @Test
    void unlearnedWordsAreNotPlanted() throws Exception {
        long weekId = launchWeek(2);
        long sessionId = startSession(weekId);
        answerCurrent(sessionId, true);
        clock.set(Instant.parse("2027-01-17T22:00:00Z"));   // Monday 00:00 in Tallinn: the week expires

        as(child, get("/api/v1/me/garden"))
                .andExpect(jsonPath("$.plantCount").value(1));
    }

    @Test
    void wateringGrowsPlantsSpelledRightAtOnce() throws Exception {
        long weekId = launchWeek(3);
        long first = startSession(weekId);
        for (int i = 0; i < 3; i++) {
            answerCurrent(first, true);
        }
        JsonNode weekBefore = read(as(parent, get("/api/v1/weeks/" + weekId)));

        clock.set(NEXT_THURSDAY);
        as(child, get("/api/v1/me/garden"))
                .andExpect(jsonPath("$.dueCount").value(3))
                .andExpect(jsonPath("$.beds[0].plants[0].due").value(true))
                .andExpect(jsonPath("$.beds[0].plants[0].thirsty").value(true));

        JsonNode review = read(as(child, post("/api/v1/me/garden/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("REVIEW"))
                .andExpect(jsonPath("$.weekId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.counts").value(false))
                .andExpect(jsonPath("$.current.roundSize").value(3)));
        long reviewId = review.get("id").asLong();
        assertThat(review.get("current").get("emoji").isTextual()).isTrue();
        // a second tap continues the same watering
        as(child, post("/api/v1/me/garden/reviews")).andExpect(jsonPath("$.id").value(reviewId));
        as(child, get("/api/v1/me/garden")).andExpect(jsonPath("$.openReviewId").value(reviewId));

        // round 1: right, wrong, right after a hint; round 2: the missed word
        answerCurrent(reviewId, true);
        answerCurrent(reviewId, false);
        JsonNode third = current(reviewId);
        as(child, post("/api/v1/sessions/" + reviewId + "/hint"),
                Map.of("weekWordId", third.get("wordId").asLong(), "round", 1)).andExpect(status().isOk());
        answerCurrent(reviewId, true);
        JsonNode done = read(as(child, post("/api/v1/sessions/" + reviewId + "/answers"), Map.of(
                "weekWordId", current(reviewId).get("wordId").asLong(), "round", 2,
                "answer", answers.get(current(reviewId).get("wordId").asLong()))));
        assertThat(done.get("sessionFinished").asBoolean()).isTrue();

        JsonNode garden = read(as(child, get("/api/v1/me/garden")));
        assertThat(garden.get("dueCount").asInt()).isZero();
        assertThat(garden.get("openReviewId").isNull()).isTrue();
        Map<String, Integer> stages = new HashMap<>();
        for (JsonNode plant : garden.get("beds").get(0).get("plants")) {
            stages.merge(plant.get("stage").asText(), 1, Integer::sum);
            assertThat(plant.get("thirsty").asBoolean()).isFalse();
        }
        assertThat(stages).isEqualTo(Map.of("SPROUT", 1, "SEED", 2));
        assertThat(nextReviews()).containsExactlyInAnyOrder("2027-02-04", "2027-01-22", "2027-01-22");

        // the failed ones want water again the next day; the grown one in two weeks
        clock.set(NEXT_THURSDAY.plusSeconds(24 * 3600));
        as(child, get("/api/v1/me/garden")).andExpect(jsonPath("$.dueCount").value(2));

        // the week and the month are untouched
        as(parent, get("/api/v1/weeks/" + weekId))
                .andExpect(jsonPath("$.points").value(weekBefore.get("points").asDouble()));
    }

    @Test
    void wateringTakesTheMostOverdueFirstUpToTheWeekSize() throws Exception {
        long weekId = launchWeek(3);
        long first = startSession(weekId);
        for (int i = 0; i < 3; i++) {
            answerCurrent(first, true);
        }
        jdbc.update("update child set default_week_size = 2 where id = ?", childId);
        jdbc.update("""
                update child_word set next_review_on = date '2027-01-19'
                where child_id = ? and concept_id = (select min(concept_id) from child_word where child_id = ?)
                """, childId, childId);
        clock.set(NEXT_THURSDAY);

        JsonNode review = read(as(child, post("/api/v1/me/garden/reviews")).andExpect(status().isOk()));
        assertThat(review.get("words")).hasSize(2);
        Long overdue = jdbc.queryForObject("""
                select ww.id from week_word ww
                where ww.week_id = ? and ww.concept_id = (select min(concept_id) from child_word where child_id = ?)
                """, Long.class, weekId, childId);
        List<Long> picked = jdbc.queryForList("select week_word_id from review_word where session_id = ?",
                Long.class, review.get("id").asLong());
        assertThat(picked).contains(overdue);
    }

    @Test
    void theGardenBelongsToTheChild() throws Exception {
        long weekId = launchWeek(1);
        answerCurrent(startSession(weekId), true);
        clock.set(NEXT_THURSDAY);
        long reviewId = read(as(child, post("/api/v1/me/garden/reviews"))).get("id").asLong();

        as(parent, get("/api/v1/me/garden")).andExpect(status().isForbidden());
        as(parent, post("/api/v1/me/garden/reviews")).andExpect(status().isForbidden());

        long siblingId = readId(as(parent, post("/api/v1/children"), Map.of("name", "Leo", "level", "A1",
                "nativeLang", "et", "targetLang", "en")));
        Session sibling = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + siblingId), parent)));
        as(sibling, get("/api/v1/sessions/" + reviewId)).andExpect(status().isNotFound());
        as(sibling, get("/api/v1/me/garden")).andExpect(jsonPath("$.plantCount").value(0));
        Session stranger = register();
        as(stranger, get("/api/v1/sessions/" + reviewId)).andExpect(status().isNotFound());
        as(parent, get("/api/v1/sessions/" + reviewId)).andExpect(status().isOk());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private List<String> nextReviews() {
        return jdbc.queryForList("select next_review_on::text from child_word where child_id = ?", String.class,
                childId);
    }

    private long launchWeek(int count) throws Exception {
        long weekId = readId(as(parent, post("/api/v1/children/" + childId + "/weeks"), Map.of("wordCount", count))
                .andExpect(status().isCreated()));
        JsonNode week = null;
        for (int i = 0; i < count; i++) {
            String[] pair = WORDS.get(i);
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

    private void answerCurrent(long sessionId, boolean correct) throws Exception {
        JsonNode item = current(sessionId);
        long wordId = item.get("wordId").asLong();
        as(child, post("/api/v1/sessions/" + sessionId + "/answers"), Map.of(
                "weekWordId", wordId, "round", item.get("round").asInt(),
                "answer", correct ? answers.get(wordId) : "wrong")).andExpect(status().isOk());
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
