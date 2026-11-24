package com.kidsvocab.app.game;

import com.kidsvocab.IntegrationTest;
import com.kidsvocab.ai.FillerRequest;
import com.kidsvocab.ai.ProviderException;
import com.kidsvocab.ai.WordSuggestionProvider;
import com.kidsvocab.domain.game.WordSearchGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Word-search grids filled with letters from the AI provider (a mock), or random ones when it fails. */
class WordSearchFillerIntegrationTest extends IntegrationTest {

    private static final List<WordSearchMaker.Word> WORDS = List.of(
            new WordSearchMaker.Word(1, "dog"), new WordSearchMaker.Word(2, "cat"),
            new WordSearchMaker.Word(3, "teddy bear"));

    @MockitoBean
    WordSuggestionProvider provider;
    @Autowired
    WordSearchMaker maker;

    @BeforeEach
    void setUp() {
        reset(provider);
        when(provider.id()).thenReturn("fake");
        maker.resetCooldown();
    }

    @Test
    void freeCellsGetTheProvidersLetters() {
        when(provider.fillerLetters(any())).thenReturn("Q".repeat(200));

        WordSearchGrid grid = maker.make(WORDS, "en", false);

        ArgumentCaptor<FillerRequest> request = ArgumentCaptor.forClass(FillerRequest.class);
        verify(provider).fillerLetters(request.capture());
        assertThat(request.getValue().lang()).isEqualTo("en");
        assertThat(request.getValue().words()).containsExactlyInAnyOrder("dog", "cat", "teddybear");
        long qs = String.join("", grid.rows()).chars().filter(c -> c == 'q').count();
        assertThat(qs).as("every free cell").isEqualTo(request.getValue().count());
        assertThat(grid.wordIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(lastLog()).containsEntry("provider", "fake").containsEntry("status", "OK");
    }

    @Test
    void randomLettersWhenTheProviderFailsAndAPauseAfterwards() {
        when(provider.fillerLetters(any())).thenThrow(
                new ProviderException(ProviderException.Kind.UNAVAILABLE, "down", null));

        WordSearchGrid first = maker.make(WORDS, "de", true);
        WordSearchGrid second = maker.make(WORDS, "de", true);

        assertThat(first.rows()).allSatisfy(r -> assertThat(r).matches("[a-zäöüß]+"));
        assertThat(second.wordIds()).hasSize(3);
        verify(provider, times(1)).fillerLetters(any());
        assertThat(lastLog()).containsEntry("status", "ERROR");
    }

    @Test
    void aBadAnswerIsLoggedButDoesNotPauseTheProvider() {
        when(provider.fillerLetters(any()))
                .thenThrow(new ProviderException(ProviderException.Kind.INVALID_RESPONSE, "junk", null))
                .thenThrow(new ProviderException(ProviderException.Kind.RATE_LIMITED, "quota", null))
                .thenReturn(null);

        maker.make(WORDS, "en", false);
        assertThat(lastLog()).containsEntry("status", "INVALID");
        maker.make(WORDS, "en", false);
        assertThat(lastLog()).containsEntry("status", "RATE_LIMIT");
        maker.resetCooldown();
        WordSearchGrid grid = maker.make(WORDS, "en", false);
        assertThat(grid.rows()).allSatisfy(r -> assertThat(r).matches("[a-z]+"));
        verify(provider, times(3)).fillerLetters(any());
    }

    private Map<String, Object> lastLog() {
        return jdbc.queryForMap("select provider, status from ai_request_log order by id desc limit 1");
    }
}
