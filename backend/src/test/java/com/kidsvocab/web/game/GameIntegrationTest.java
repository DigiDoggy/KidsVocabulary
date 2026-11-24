package com.kidsvocab.web.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.kidsvocab.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Game modes: the child's games with this week's words, and game tasks given by the parent. */
class GameIntegrationTest extends IntegrationTest {

    private static final Instant WEDNESDAY = Instant.parse("2027-01-13T10:00:00Z");
    private static final List<String[]> WORDS = List.of(
            new String[]{"koer", "dog"}, new String[]{"kass", "cat"}, new String[]{"lehm", "cow"},
            new String[]{"siga", "pig"}, new String[]{"lammas", "sheep"});

    private Session parent;
    private Session child;
    private long childId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(WEDNESDAY);
        parent = register();
        childId = read(as(parent, post("/api/v1/children"), Map.of("name", "Mia", "level", "A1",
                "nativeLang", "et", "targetLang", "en"))).get("id").asLong();
        child = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + childId), parent)));
    }

    // --- this week's words -----------------------------------------------------------------------

    @Test
    void withoutASetThisWeekThereIsNothingToPlay() throws Exception {
        JsonNode menu = read(as(child, get("/api/v1/me/games")).andExpect(status().isOk()));
        assertThat(menu.get("week").isNull()).isTrue();
        assertThat(menu.get("tasks")).isEmpty();
        as(child, get("/api/v1/me/games/week/CHOICE")).andExpect(status().isConflict());
    }

    @Test
    void theWeekCanBePlayedInEveryMode() throws Exception {
        long weekId = launchWeek();

        JsonNode menu = read(as(child, get("/api/v1/me/games")));
        assertThat(menu.get("week").get("id").asLong()).isEqualTo(weekId);
        assertThat(menu.get("week").get("wordCount").asInt()).isEqualTo(5);

        JsonNode choice = read(as(child, get("/api/v1/me/games/week/CHOICE")).andExpect(status().isOk()));
        assertThat(choice.get("mode").asText()).isEqualTo("CHOICE");
        assertThat(choice.get("taskId").isNull()).isTrue();
        assertThat(choice.get("grid").isNull()).isTrue();
        assertThat(choice.get("words")).hasSize(5);
        for (JsonNode w : choice.get("words")) {
            List<String> options = texts(w.get("options"));
            assertThat(options).hasSize(4).contains(w.get("answer").asText()).doesNotHaveDuplicates();
            assertThat(options).as("distractors from the same list first")
                    .isSubsetOf("dog", "cat", "cow", "pig", "sheep");
        }

        JsonNode scramble = read(as(child, get("/api/v1/me/games/week/SCRAMBLE")));
        assertThat(texts(scramble.get("words"), "answer")).containsExactly("dog", "cat", "cow", "pig", "sheep");
        assertThat(texts(scramble.get("words"), "prompt")).containsExactly("koer", "kass", "lehm", "siga", "lammas");
        assertThat(scramble.get("words").get(0).get("options").isNull()).isTrue();

        JsonNode search = read(as(child, get("/api/v1/me/games/week/WORD_SEARCH")));
        JsonNode grid = search.get("grid");
        int size = grid.get("size").asInt();
        assertThat(size).isEqualTo(6);
        assertThat(grid.get("rows")).hasSize(size);
        List<String> rows = texts(grid.get("rows"));
        assertThat(rows).allSatisfy(r -> assertThat(r).hasSize(size).matches("[a-z]+"));
        assertThat(search.get("words")).hasSize(5);
        for (String answer : texts(search.get("words"), "answer")) {
            assertThat(appearsIn(rows, answer)).as(answer).isTrue();
        }
        JsonNode again = read(as(child, get("/api/v1/me/games/week/WORD_SEARCH")));
        assertThat(again.get("grid")).as("the grid is kept").isEqualTo(grid);

        as(child, get("/api/v1/me/games/week/TYPING")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from training_session where week_id = ?", Integer.class, weekId))
                .as("games never start a training session").isZero();
    }

    @Test
    void gamesAreForTheChildOnly() throws Exception {
        as(parent, get("/api/v1/me/games")).andExpect(status().isForbidden());
        as(child, get("/api/v1/children/" + childId + "/game-tasks")).andExpect(status().isForbidden());
    }

    // --- tasks -----------------------------------------------------------------------------------

    @Test
    void parentPreparesAndGivesATask() throws Exception {
        long taskId = createTask("CHOICE");
        JsonNode task = null;
        for (String[] pair : WORDS.subList(0, 3)) {
            task = read(as(parent, post("/api/v1/game-tasks/" + taskId + "/words"),
                    Map.of("source", pair[0], "target", pair[1])).andExpect(status().isOk()));
        }
        assertThat(task.get("status").asText()).isEqualTo("DRAFT");
        assertThat(task.get("minWords").asInt()).isEqualTo(3);
        assertThat(task.get("maxWords").asInt()).isEqualTo(12);
        assertThat(texts(task.get("words"), "answer")).containsExactly("dog", "cat", "cow");

        assertThat(read(as(child, get("/api/v1/me/games"))).get("tasks")).as("a draft is not shown").isEmpty();
        as(child, get("/api/v1/me/games/tasks/" + taskId)).andExpect(status().isNotFound());

        task = read(as(parent, post("/api/v1/game-tasks/" + taskId + "/suggest"), Map.of("topic", "animals", "count", 2))
                .andExpect(status().isOk()));
        assertThat(task.get("words")).hasSize(5);

        as(parent, post("/api/v1/game-tasks/" + taskId + "/give")).andExpect(jsonPath("$.status").value("GIVEN"));
        as(parent, post("/api/v1/game-tasks/" + taskId + "/words"), Map.of("source", "siga", "target", "pig"))
                .andExpect(status().isConflict());

        JsonNode menu = read(as(child, get("/api/v1/me/games")));
        assertThat(menu.get("tasks")).hasSize(1);
        assertThat(menu.get("tasks").get(0).get("mode").asText()).isEqualTo("CHOICE");
        assertThat(menu.get("tasks").get(0).get("wordCount").asInt()).isEqualTo(5);

        JsonNode game = read(as(child, get("/api/v1/me/games/tasks/" + taskId)).andExpect(status().isOk()));
        assertThat(game.get("taskId").asLong()).isEqualTo(taskId);
        assertThat(game.get("words")).hasSize(5)
                .allSatisfy(w -> assertThat(texts(w.get("options"))).hasSize(4).contains(w.get("answer").asText()));

        as(child, post("/api/v1/me/games/tasks/" + taskId + "/played")).andExpect(status().isNoContent());
        as(child, post("/api/v1/me/games/tasks/" + taskId + "/played")).andExpect(status().isNoContent());
        JsonNode list = read(as(parent, get("/api/v1/children/" + childId + "/game-tasks")));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("timesPlayed").asInt()).isEqualTo(2);
        assertThat(list.get(0).get("wordCount").asInt()).isEqualTo(5);

        as(parent, delete("/api/v1/game-tasks/" + taskId)).andExpect(status().isNoContent());
        assertThat(read(as(child, get("/api/v1/me/games"))).get("tasks")).isEmpty();
    }

    @Test
    void aWordSearchTaskGetsItsGridWhenGiven() throws Exception {
        long taskId = createTask("WORD_SEARCH");
        as(parent, post("/api/v1/game-tasks/" + taskId + "/words"), Map.of("source", "liblikas", "target", "caterpillars"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("too long")));
        for (String[] pair : WORDS.subList(0, 3)) {
            as(parent, post("/api/v1/game-tasks/" + taskId + "/words"), Map.of("source", pair[0], "target", pair[1]))
                    .andExpect(status().isOk());
        }
        JsonNode task = read(as(parent, post("/api/v1/game-tasks/" + taskId + "/give")));
        assertThat(task.get("maxWords").asInt()).isEqualTo(8);

        JsonNode game = read(as(child, get("/api/v1/me/games/tasks/" + taskId)));
        assertThat(game.get("mode").asText()).isEqualTo("WORD_SEARCH");
        List<String> rows = texts(game.get("grid").get("rows"));
        assertThat(texts(game.get("words"), "answer")).containsExactlyInAnyOrder("dog", "cat", "cow")
                .allSatisfy(a -> assertThat(appearsIn(rows, a)).isTrue());
    }

    @Test
    void taskRules() throws Exception {
        long taskId = createTask("SCRAMBLE");
        as(parent, post("/api/v1/game-tasks/" + taskId + "/give")).andExpect(status().isBadRequest());
        as(parent, post("/api/v1/game-tasks/" + taskId + "/words"), Map.of("source", "koer", "target", "dog"))
                .andExpect(status().isOk());
        as(parent, post("/api/v1/game-tasks/" + taskId + "/words"), Map.of("source", "koer", "target", "dog"))
                .andExpect(status().isConflict());
        as(parent, post("/api/v1/game-tasks/" + taskId + "/words"), Map.of("source", "koer", "target", "koer"))
                .andExpect(status().isBadRequest());
        JsonNode task = read(as(parent, post("/api/v1/game-tasks/" + taskId + "/suggest"), Map.of()));
        assertThat(task.get("words")).as("filled up to the maximum").hasSize(12);
        Set<String> answers = new HashSet<>(texts(task.get("words"), "answer"));
        assertThat(answers).hasSize(12);
        as(parent, post("/api/v1/game-tasks/" + taskId + "/suggest"), Map.of()).andExpect(status().isConflict());
        as(parent, post("/api/v1/game-tasks/" + taskId + "/words"), Map.of("source", "siga", "target", "pig"))
                .andExpect(status().isConflict());

        long wordId = task.get("words").get(0).get("id").asLong();
        task = read(as(parent, delete("/api/v1/game-tasks/" + taskId + "/words/" + wordId)).andExpect(status().isOk()));
        assertThat(task.get("words")).hasSize(11);
        as(parent, delete("/api/v1/game-tasks/" + taskId + "/words/" + wordId)).andExpect(status().isNotFound());

        as(parent, post("/api/v1/children/" + childId + "/game-tasks"), Map.of("mode", "TYPING"))
                .andExpect(status().isBadRequest());
        as(parent, post("/api/v1/children/" + childId + "/game-tasks"), Map.of()).andExpect(status().isBadRequest());

        Session stranger = register();
        as(stranger, get("/api/v1/game-tasks/" + taskId)).andExpect(status().isNotFound());
        as(stranger, get("/api/v1/children/" + childId + "/game-tasks")).andExpect(status().isNotFound());
        as(child, post("/api/v1/me/games/tasks/" + taskId + "/played")).andExpect(status().isNotFound());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private long launchWeek() throws Exception {
        long weekId = read(as(parent, post("/api/v1/children/" + childId + "/weeks"), Map.of("wordCount", 5))
                .andExpect(status().isCreated())).get("id").asLong();
        for (String[] pair : WORDS) {
            as(parent, post("/api/v1/weeks/" + weekId + "/words"), Map.of("source", pair[0], "target", pair[1]))
                    .andExpect(status().isOk());
        }
        as(parent, post("/api/v1/weeks/" + weekId + "/launch")).andExpect(jsonPath("$.status").value("ACTIVE"));
        return weekId;
    }

    private long createTask(String mode) throws Exception {
        return read(as(parent, post("/api/v1/children/" + childId + "/game-tasks"), Map.of("mode", mode))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** The word read left to right, top to bottom or diagonally down to the right. */
    static boolean appearsIn(List<String> rows, String word) {
        int size = rows.size();
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}};
        for (int[] d : directions) {
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < word.length(); i++) {
                        int rr = r + i * d[0];
                        int cc = c + i * d[1];
                        if (rr >= size || cc >= size) {
                            break;
                        }
                        sb.append(rows.get(rr).charAt(cc));
                    }
                    if (sb.toString().equals(word)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<String> texts(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.asText()));
        return list;
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.get(field).asText()));
        return list;
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
}
