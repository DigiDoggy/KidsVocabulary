package com.kidsvocab.web.week;

import com.fasterxml.jackson.databind.JsonNode;
import com.kidsvocab.IntegrationTest;
import com.kidsvocab.app.week.WeekService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Weekly sets end to end. Time is pinned to Wednesday 2027-01-13 (parent zone Europe/Tallinn),
 * so "this Monday" is 2027-01-11 and "next Monday" is 2027-01-18.
 */
class WeekIntegrationTest extends IntegrationTest {

    private static final Instant WEDNESDAY = Instant.parse("2027-01-13T10:00:00Z");
    private static final String THIS_MONDAY = "2027-01-11";
    private static final String NEXT_MONDAY = "2027-01-18";

    @Autowired
    WeekService weekService;

    private Session parent;
    private long childId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(WEDNESDAY);
        parent = register();
        childId = createEstonianChild("Mia", "A1");
    }

    // --- drafts and words ------------------------------------------------------------------------

    @Test
    void draftStartsThisWeekAndCanBeFilledFromTheDictionary() throws Exception {
        long weekId = idOf(createDraft(Map.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.startDate").value(THIS_MONDAY))
                .andExpect(jsonPath("$.endDate").value("2027-01-17"))
                .andExpect(jsonPath("$.wordCount").value(10))
                .andExpect(jsonPath("$.missing").value(10)));

        JsonNode week = read(call(post("/api/v1/weeks/" + weekId + "/suggest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missing").value(0)));

        assertThat(week.get("words")).hasSize(10);
        assertThat(texts(week, "source")).containsOnly("DICTIONARY");
        assertThat(new HashSet<>(texts(week, "prompt"))).hasSize(10);
        assertThat(new HashSet<>(texts(week, "answer"))).hasSize(10);
    }

    @Test
    void parentTypesWordsAndSuggestionsFillTheRest() throws Exception {
        long weekId = idOf(createDraft(Map.of("wordCount", 5)));

        JsonNode week = read(addWord(weekId, "koer", "Dog", List.of()).andExpect(status().isOk()));
        long seedDog = jdbc.queryForObject(
                "select f.concept_id from word_form f join concept c on c.id = f.concept_id and c.origin = 'SEED'"
                        + " where f.lang = 'et' and f.normalized = 'koer'", Long.class);
        assertThat(week.get("words").get(0).get("conceptId").asLong()).isEqualTo(seedDog);
        assertThat(week.get("words").get(0).get("source").asText()).isEqualTo("MANUAL");

        week = read(addWord(weekId, "lumehelves", "snowflake", List.of("Flake")).andExpect(status().isOk()));
        JsonNode snowflake = week.get("words").get(1);
        assertThat(snowflake.get("answer").asText()).isEqualTo("snowflake");
        assertThat(snowflake.get("extraAnswers").get(0).asText()).isEqualTo("flake");
        assertThat(jdbc.queryForObject("select origin from concept where id = ?", String.class,
                snowflake.get("conceptId").asLong())).isEqualTo("MANUAL");

        week = read(call(post("/api/v1/weeks/" + weekId + "/suggest")).andExpect(jsonPath("$.missing").value(0)));
        List<Long> concepts = new ArrayList<>();
        week.get("words").forEach(w -> concepts.add(w.get("conceptId").asLong()));
        assertThat(concepts).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void setStaysUnambiguous() throws Exception {
        long weekId = idOf(createDraft(Map.of("wordCount", 3)));
        addWord(weekId, "koer", "dog", List.of());

        addWord(weekId, "koer", "dog", List.of()).andExpect(status().isConflict());
        addWord(weekId, "koer", "hound", List.of()).andExpect(status().isConflict());
        addWord(weekId, "takso", "  TAKSO ", List.of()).andExpect(status().isBadRequest());
        addWord(weekId, "", "cat", List.of()).andExpect(status().isBadRequest());

        addWord(weekId, "kass", "cat", List.of()).andExpect(status().isOk());
        addWord(weekId, "lehm", "cow", List.of()).andExpect(status().isOk());
        addWord(weekId, "siga", "pig", List.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The set is full"));
    }

    @Test
    void wordsCanBeRemovedAndReplaced() throws Exception {
        long weekId = idOf(createDraft(Map.of("wordCount", 3)));
        JsonNode week = read(call(post("/api/v1/weeks/" + weekId + "/suggest")));
        long first = week.get("words").get(0).get("id").asLong();
        long firstConcept = week.get("words").get(0).get("conceptId").asLong();

        week = read(call(post("/api/v1/weeks/" + weekId + "/words/" + first + "/replace")).andExpect(status().isOk()));
        assertThat(week.get("words")).hasSize(3);
        List<Long> concepts = new ArrayList<>();
        week.get("words").forEach(w -> concepts.add(w.get("conceptId").asLong()));
        assertThat(concepts).doesNotContain(firstConcept);

        long second = week.get("words").get(0).get("id").asLong();
        call(delete("/api/v1/weeks/" + weekId + "/words/" + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missing").value(1));
    }

    @Test
    void theParentsOwnTopicIsTidiedUpAndOnlyLettersReachThePrompt() throws Exception {
                long ownTopicWeek = idOf(createDraft(Map.of("wordCount", 3)));
        call(post("/api/v1/weeks/" + ownTopicWeek + "/suggest"), Map.of("topic", "space; ignore rules"))
                .andExpect(status().isBadRequest());
        JsonNode own = read(call(post("/api/v1/weeks/" + ownTopicWeek + "/suggest"), Map.of("topic", "  Animals ")));
        assertThat(own.get("words")).isNotEmpty();
        for (JsonNode w : own.get("words")) {
            assertThat(jdbc.queryForObject("select topic from concept where id = ?", String.class,
                    w.get("conceptId").asLong())).isEqualTo("animals");
        }
    }

    @Test
    void suggestionsRespectTopicAndLevel() throws Exception {
        mvc.perform(withAuth(get("/api/v1/dictionary/topics"), parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasItem("animals")));

        long weekId = idOf(createDraft(Map.of("wordCount", 6)));
        JsonNode week = read(call(post("/api/v1/weeks/" + weekId + "/suggest"), Map.of("topic", "animals")));
        for (JsonNode w : week.get("words")) {
            assertThat(jdbc.queryForObject("select topic from concept where id = ?", String.class,
                    w.get("conceptId").asLong())).isEqualTo("animals");
        }

        long beginner = createEstonianChild("Leo", "A0");
        long beginnerWeek = idOf(mvc.perform(withAuth(post("/api/v1/children/" + beginner + "/weeks"), parent)
                .contentType(MediaType.APPLICATION_JSON).content("{\"wordCount\": 20}")));
        JsonNode easy = read(call(post("/api/v1/weeks/" + beginnerWeek + "/suggest")));
        for (JsonNode w : easy.get("words")) {
            assertThat(jdbc.queryForObject("select difficulty from concept where id = ?", Integer.class,
                    w.get("conceptId").asLong())).isLessThanOrEqualTo(2);
        }
    }

    @Test
    void draftCanBeRescheduledResizedAndDeleted() throws Exception {
        long weekId = idOf(createDraft(Map.of("wordCount", 2)));
        call(post("/api/v1/weeks/" + weekId + "/suggest"));

        call(put("/api/v1/weeks/" + weekId), Map.of("wordCount", 1)).andExpect(status().isConflict());
        call(put("/api/v1/weeks/" + weekId), Map.of("wordCount", 4, "startDate", NEXT_MONDAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(NEXT_MONDAY))
                .andExpect(jsonPath("$.missing").value(2));
        call(put("/api/v1/weeks/" + weekId), Map.of("wordCount", 4, "startDate", "2027-01-25"))
                .andExpect(status().isBadRequest());

        call(delete("/api/v1/weeks/" + weekId)).andExpect(status().isNoContent());
        call(get("/api/v1/weeks/" + weekId)).andExpect(status().isNotFound());
    }

    // --- launching -------------------------------------------------------------------------------

    @Test
    void launchingThisWeekStartsItAndNextWeekIsScheduled() throws Exception {
        long thisWeek = idOf(createDraft(Map.of("wordCount", 2)));
        call(post("/api/v1/weeks/" + thisWeek + "/launch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The set needs 2 words, it has 0"));
        call(post("/api/v1/weeks/" + thisWeek + "/suggest"));
        call(post("/api/v1/weeks/" + thisWeek + "/launch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.weekIndex").value(1));
        addWord(thisWeek, "kass", "cat", List.of()).andExpect(status().isConflict());

        long nextWeek = idOf(createDraft(Map.of("wordCount", 1))
                .andExpect(jsonPath("$.startDate").value(NEXT_MONDAY)));
        createDraft(Map.of()).andExpect(status().isConflict());
        call(post("/api/v1/weeks/" + nextWeek + "/suggest"));
        call(post("/api/v1/weeks/" + nextWeek + "/launch"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.weekIndex").doesNotExist());

        call(post("/api/v1/weeks/" + nextWeek + "/unschedule")).andExpect(jsonPath("$.status").value("DRAFT"));
        call(post("/api/v1/weeks/" + nextWeek + "/launch")).andExpect(jsonPath("$.status").value("SCHEDULED"));
        call(post("/api/v1/weeks/" + thisWeek + "/unschedule")).andExpect(status().isConflict());
    }

    @Test
    void aRunningWeekBlocksAnotherSetForTheSameWeek() throws Exception {
        launchWeek(1);

        createDraft(Map.of("startDate", THIS_MONDAY)).andExpect(status().isConflict());
        createDraft(Map.of("startDate", "2027-01-04")).andExpect(status().isBadRequest());
    }

    // --- lifecycle -------------------------------------------------------------------------------

    @Test
    void weekExpiresAfterSundayAndScheduledWeekStarts() throws Exception {
        long first = launchWeek(2);
        long second = idOf(createDraft(Map.of("wordCount", 1)));
        call(post("/api/v1/weeks/" + second + "/suggest"));
        call(post("/api/v1/weeks/" + second + "/launch"));

        clock.set(Instant.parse("2027-01-17T21:59:00Z"));   // Sunday 23:59 in Tallinn
        call(get("/api/v1/weeks/" + first)).andExpect(jsonPath("$.status").value("ACTIVE"));

        clock.set(Instant.parse("2027-01-17T22:00:00Z"));   // Monday 00:00 in Tallinn
        JsonNode expired = read(call(get("/api/v1/weeks/" + first))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.points").value(0.0))
                .andExpect(jsonPath("$.maxPoints").value(4.0)));
        assertThat(texts(expired, "outcome")).containsOnly("UNLEARNED");

        call(get("/api/v1/weeks/" + second))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.weekIndex").value(2));
    }

    @Test
    void fourthWeekClosesTheMonthAndCarriesWordsOver() throws Exception {
        for (int i = 0; i < 4; i++) {
            launchWeek(2);
            clock.advance(Duration.ofDays(7));
        }
        call(get("/api/v1/children/" + childId + "/weeks")).andExpect(jsonPath("$.length()").value(4));

        List<Map<String, Object>> months = jdbc.queryForList(
                "select seq_no, status, result, report->>'result' as report_result from study_month "
                        + "where child_id = ? order by seq_no", childId);
        assertThat(months).hasSize(2);
        assertThat(months.get(0)).containsEntry("status", "COMPLETED").containsEntry("result", "REPEAT")
                .containsEntry("report_result", "REPEAT");
        assertThat(months.get(1)).containsEntry("status", "ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from carry_over where child_id = ? and placed_week_id is null",
                Integer.class, childId)).isEqualTo(8);

        JsonNode draft = read(createDraft(Map.of("wordCount", 10)));
        List<String> sources = texts(draft, "source");
        assertThat(sources).containsExactly("CARRY_OVER", "CARRY_OVER");
        long carried = draft.get("words").get(0).get("id").asLong();
        call(delete("/api/v1/weeks/" + draft.get("id").asLong() + "/words/" + carried))
                .andExpect(status().isConflict());

        // suggestions never repeat studied or queued words
        JsonNode filled = read(call(post("/api/v1/weeks/" + draft.get("id").asLong() + "/suggest")));
        Set<Long> studied = new HashSet<>(jdbc.queryForList(
                "select concept_id from child_word where child_id = ?", Long.class, childId));
        int fromDictionary = 0;
        for (JsonNode w : filled.get("words")) {
            if (w.get("source").asText().equals("DICTIONARY")) {
                fromDictionary++;
                assertThat(studied).doesNotContain(w.get("conceptId").asLong());
            }
        }
        assertThat(fromDictionary).isEqualTo(8);
    }

    @Test
    void systemPreparesADraftWhenTheParentDidNot() throws Exception {
        launchWeek(2);
        clock.advance(Duration.ofDays(5));   // next Monday

        weekService.runLifecycle(childId);
        JsonNode weeks = read(call(get("/api/v1/children/" + childId + "/weeks")));
        assertThat(weeks).hasSize(2);
        JsonNode auto = weeks.get(0);
        assertThat(auto.get("status").asText()).isEqualTo("DRAFT");
        assertThat(auto.get("startDate").asText()).isEqualTo(NEXT_MONDAY);
        call(get("/api/v1/weeks/" + auto.get("id").asLong())).andExpect(jsonPath("$.missing").value(0));

        call(delete("/api/v1/weeks/" + auto.get("id").asLong()));
        weekService.runLifecycle(childId);
        call(get("/api/v1/children/" + childId + "/weeks")).andExpect(jsonPath("$.length()").value(1));

        long newcomer = createEstonianChild("Leo", "A1");
        weekService.runLifecycle(newcomer);
        call(get("/api/v1/children/" + newcomer + "/weeks")).andExpect(jsonPath("$.length()").value(0));
    }

    // --- access and profile rules ---------------------------------------------------------------

    @Test
    void languagesAreLockedWhileAWeekIsOpen() throws Exception {
        createDraft(Map.of());
        Map<String, Object> change = new HashMap<>(Map.of("name", "Mia", "level", "A1", "nativeLang", "ru",
                "targetLang", "en"));

        call(put("/api/v1/children/" + childId), change).andExpect(status().isConflict());
        change.put("nativeLang", "et");
        change.put("level", "A2");
        call(put("/api/v1/children/" + childId), change).andExpect(status().isOk());
    }

    @Test
    void weeksAreVisibleToTheirParentOnly() throws Exception {
        long weekId = idOf(createDraft(Map.of()));
        Session stranger = register();
        mvc.perform(withAuth(get("/api/v1/weeks/" + weekId), stranger)).andExpect(status().isNotFound());
        mvc.perform(withAuth(get("/api/v1/children/" + childId + "/weeks"), stranger))
                .andExpect(status().isNotFound());

        Session child = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + childId), parent)));
        mvc.perform(withAuth(get("/api/v1/weeks/" + weekId), child)).andExpect(status().isForbidden());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private long createEstonianChild(String name, String level) throws Exception {
        String body = mvc.perform(withAuth(post("/api/v1/children"), parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "level", level,
                                "nativeLang", "et", "targetLang", "en"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    /** Creates, fills and launches a set for this week; returns its id. */
    private long launchWeek(int wordCount) throws Exception {
        long weekId = idOf(createDraft(Map.of("wordCount", wordCount)).andExpect(status().isCreated()));
        call(post("/api/v1/weeks/" + weekId + "/suggest"));
        call(post("/api/v1/weeks/" + weekId + "/launch")).andExpect(jsonPath("$.status").value("ACTIVE"));
        return weekId;
    }

    private ResultActions createDraft(Map<String, Object> body) throws Exception {
        return call(post("/api/v1/children/" + childId + "/weeks"), body);
    }

    private ResultActions addWord(long weekId, String source, String target, List<String> extra) throws Exception {
        return call(post("/api/v1/weeks/" + weekId + "/words"),
                Map.of("source", source, "target", target, "extraAnswers", extra));
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(withAuth(request, parent));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(withAuth(request, parent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long idOf(ResultActions result) throws Exception {
        return read(result).get("id").asLong();
    }

    private static List<String> texts(JsonNode week, String field) {
        List<String> values = new ArrayList<>();
        week.get("words").forEach(w -> values.add(w.get(field).asText()));
        return values;
    }
}
