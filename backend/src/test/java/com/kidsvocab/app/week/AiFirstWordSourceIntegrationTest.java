package com.kidsvocab.app.week;

import com.fasterxml.jackson.databind.JsonNode;
import com.kidsvocab.IntegrationTest;
import com.kidsvocab.ai.ProviderException;
import com.kidsvocab.ai.SuggestionRequest;
import com.kidsvocab.ai.WordSuggestionProvider;
import com.kidsvocab.core.suggestion.SuggestedWord;
import com.kidsvocab.domain.child.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Fill up" with an AI provider in front of the dictionary. The provider is a mock. */
class AiFirstWordSourceIntegrationTest extends IntegrationTest {

    private static final String UMBRELLA = new String(Character.toChars(0x2602)) + "\uFE0F";

    @MockitoBean
    WordSuggestionProvider provider;
    @Autowired
    AiFirstWordSource source;

    private Session parent;
    private long childId;

    @BeforeEach
    void setUp() throws Exception {
        reset(provider);
        when(provider.id()).thenReturn("fake");
        source.resetCooldown();
        parent = register();
        childId = createChild();
    }

    @Test
    void aiWordsAreValidatedStoredAndUsed() throws Exception {
        when(provider.suggest(any())).thenReturn(List.of(
                new SuggestedWord("vihmavari", "umbrella", "home", "umbrella", UMBRELLA),
                new SuggestedWord("koer", "dog", "animals", "dog", UMBRELLA),  // known seed pair: reused as it is
                new SuggestedWord("kass", "kass", "animals"),         // translation equals the word
                new SuggestedWord("tee", "road", "transport"),        // "tee" already means tea
                new SuggestedWord("kaelkirjak", "giraffe", "animals")));

        JsonNode week = fill(3);

        assertThat(texts(week, "source")).containsOnly("AI");
        assertThat(texts(week, "answer")).containsExactly("umbrella", "dog", "giraffe");
        long umbrella = week.get("words").get(0).get("conceptId").asLong();
        assertThat(jdbc.queryForMap("select origin, owner_parent_id, topic, emoji from concept where id = ?", umbrella))
                .containsEntry("origin", "AI").containsEntry("owner_parent_id", null).containsEntry("topic", "home")
                .containsEntry("emoji", UMBRELLA);
        assertThat(week.get("words").get(0).get("emoji").asText()).isEqualTo(UMBRELLA);
        assertThat(week.get("words").get(1).get("emoji").asText()).as("seed picture kept")
                .isEqualTo(new String(Character.toChars(0x1F415)));
        long seedDog = jdbc.queryForObject("select f.concept_id from word_form f join concept c on c.id = f.concept_id "
                + "where c.origin = 'SEED' and f.lang = 'en' and f.normalized = 'dog'", Long.class);
        assertThat(week.get("words").get(1).get("conceptId").asLong()).isEqualTo(seedDog);
        assertThat(lastLog()).containsEntry("provider", "fake").containsEntry("status", "OK")
                .containsEntry("words_in", 5).containsEntry("words_valid", 3);
    }

    @Test
    void germanWordsCompleteMatchingDictionaryConcepts() throws Exception {
        childId = createChild("de");
        when(provider.suggest(any())).thenReturn(List.of(
                new SuggestedWord("koer", "Hund", "animals", "dog"),        // seed "koer = dog" gets its German word
                new SuggestedWord("tee", "Weg", "transport", "road"),       // "tee" is tea in the dictionary: dropped
                new SuggestedWord("kass", "Katze", "animals", null),        // meaning not confirmed: dropped
                new SuggestedWord("kaelkirjak", "Giraffe", "animals", "giraffe")));

        JsonNode week = fill(2);

        assertThat(texts(week, "answer")).containsExactly("Hund", "Giraffe");
        long seedDog = jdbc.queryForObject("select f.concept_id from word_form f join concept c on c.id = f.concept_id "
                + "where c.origin = 'SEED' and f.lang = 'en' and f.normalized = 'dog'", Long.class);
        assertThat(week.get("words").get(0).get("conceptId").asLong()).isEqualTo(seedDog);
        assertThat(jdbc.queryForObject("select text from word_form where concept_id = ? and lang = 'de'",
                String.class, seedDog)).isEqualTo("Hund");
        long seedTea = jdbc.queryForObject("select f.concept_id from word_form f join concept c on c.id = f.concept_id "
                + "where c.origin = 'SEED' and f.lang = 'en' and f.normalized = 'tea'", Long.class);
        assertThat(jdbc.queryForObject("select count(*) from word_form where concept_id = ? and lang = 'de'",
                Integer.class, seedTea)).isZero();
    }

    @Test
    void requestCarriesOnlyNonPersonalDataAndExclusions() throws Exception {
        when(provider.suggest(any())).thenReturn(List.of());
        long weekId = createDraft(4);
        addWord(weekId, "koer", "dog");

        fill(weekId);

        ArgumentCaptor<SuggestionRequest> captor = ArgumentCaptor.forClass(SuggestionRequest.class);
        verify(provider, times(2)).suggest(captor.capture());
        SuggestionRequest request = captor.getAllValues().getFirst();
        assertThat(request.childAge()).isBetween(7, 9);
        assertThat(request.level()).isEqualTo(Level.A1);
        assertThat(request.nativeLang()).isEqualTo("et");
        assertThat(request.targetLang()).isEqualTo("en");
        assertThat(request.count()).isEqualTo(3 + 2);
        assertThat(request.excludeTargetWords()).contains("dog");
        assertThat(request.topics()).contains("animals", "food");
    }

    @Test
    void tooFewGoodWordsAreToppedUpFromTheDictionary() throws Exception {
        when(provider.suggest(any())).thenReturn(List.of(new SuggestedWord("vihmavari", "umbrella", null)));

        JsonNode week = fill(4);

        verify(provider, times(2)).suggest(any());
        assertThat(texts(week, "source")).containsExactly("AI", "DICTIONARY", "DICTIONARY", "DICTIONARY");
    }

    @Test
    void failingProviderFallsBackAndIsSkippedForAWhile() throws Exception {
        when(provider.suggest(any())).thenThrow(
                new ProviderException(ProviderException.Kind.RATE_LIMITED, "fake answered HTTP 429", null));

        JsonNode week = fill(3);
        assertThat(texts(week, "source")).containsOnly("DICTIONARY");
        assertThat(lastLog()).containsEntry("status", "RATE_LIMIT").containsEntry("error", "fake answered HTTP 429");

        long secondChild = createChild();
        mvc.perform(withAuth(post("/api/v1/children/" + secondChild + "/weeks"), parent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"wordCount\": 2}"))
                .andExpect(status().isCreated());
        long otherWeek = jdbc.queryForObject("select id from study_week where child_id = ?", Long.class, secondChild);
        fill(otherWeek);

        verify(provider, times(1)).suggest(any());
    }

    @Test
    void busyProviderIsAskedOnceMore() throws Exception {
        when(provider.suggest(any()))
                .thenThrow(new ProviderException(ProviderException.Kind.UNAVAILABLE, "fake answered HTTP 503", null))
                .thenReturn(List.of(new SuggestedWord("vihmavari", "umbrella", null),
                        new SuggestedWord("kaelkirjak", "giraffe", null)));

        JsonNode week = fill(2);

        assertThat(texts(week, "source")).containsOnly("AI");
        verify(provider, times(2)).suggest(any());
        assertThat(week.get("fill").get("requested").asInt()).isEqualTo(2);
        assertThat(week.get("fill").get("added").asInt()).isEqualTo(2);
        assertThat(week.get("fill").get("aiAvailable").asBoolean()).isTrue();
    }

    @Test
    void ownTopicWithAProviderDownAddsNothingAndSaysWhy() throws Exception {
        when(provider.suggest(any())).thenThrow(
                new ProviderException(ProviderException.Kind.UNAVAILABLE, "fake answered HTTP 503", null));
        long weekId = createDraft(3);

        String body = mvc.perform(withAuth(post("/api/v1/weeks/" + weekId + "/suggest"), parent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"weather\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode week = json.readTree(body);

        verify(provider, times(2)).suggest(any());
        assertThat(week.get("words")).isEmpty();
        assertThat(week.get("fill").get("requested").asInt()).isEqualTo(3);
        assertThat(week.get("fill").get("added").asInt()).isZero();
        assertThat(week.get("fill").get("aiAvailable").asBoolean()).isFalse();
    }

    @Test
    void invalidAnswerIsRetriedWithoutCooldown() throws Exception {
        when(provider.suggest(any()))
                .thenThrow(new ProviderException(ProviderException.Kind.INVALID_RESPONSE, "not JSON", null))
                .thenReturn(List.of(new SuggestedWord("vihmavari", "umbrella", null),
                        new SuggestedWord("kaelkirjak", "giraffe", null)));

        JsonNode week = fill(2);

        assertThat(texts(week, "source")).containsOnly("AI");
        verify(provider, times(2)).suggest(any());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private long createChild() throws Exception {
        return createChild("en");
    }

    private long createChild(String targetLang) throws Exception {
        String body = mvc.perform(withAuth(post("/api/v1/children"), parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Mia", "level", "A1", "nativeLang", "et",
                                "targetLang", targetLang, "birthDate", LocalDate.now().minusYears(8).minusDays(10).toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createDraft(int wordCount) throws Exception {
        String body = mvc.perform(withAuth(post("/api/v1/children/" + childId + "/weeks"), parent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"wordCount\": " + wordCount + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void addWord(long weekId, String source, String target) throws Exception {
        mvc.perform(withAuth(post("/api/v1/weeks/" + weekId + "/words"), parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("source", source, "target", target))))
                .andExpect(status().isOk());
    }

    private JsonNode fill(int wordCount) throws Exception {
        return fill(createDraft(wordCount));
    }

    private JsonNode fill(long weekId) throws Exception {
        String body = mvc.perform(withAuth(post("/api/v1/weeks/" + weekId + "/suggest"), parent))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private Map<String, Object> lastLog() {
        return jdbc.queryForMap("select provider, status, words_in, words_valid, error from ai_request_log "
                + "order by id desc limit 1");
    }

    private static List<String> texts(JsonNode week, String field) {
        List<String> values = new ArrayList<>();
        week.get("words").forEach(w -> values.add(w.get(field).asText()));
        return values;
    }
}
