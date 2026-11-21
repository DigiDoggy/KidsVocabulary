package com.kidsvocab.db;

import com.kidsvocab.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the quality of the starter dictionary loaded by Flyway. */
class SeedDictionaryIntegrationTest extends IntegrationTest {

    @Test
    void containsSeveralHundredSeedConcepts() {
        Integer seeds = jdbc.queryForObject("select count(*) from concept where origin = 'SEED'", Integer.class);

        assertThat(seeds).isGreaterThanOrEqualTo(250);
    }

    @Test
    void everyConceptHasOnePrimaryFormPerLanguage() {
        List<Long> broken = jdbc.queryForList("""
                select c.id from concept c
                where c.origin = 'SEED'
                  and (select count(*) from word_form f
                       where f.concept_id = c.id and f.is_primary and f.lang in ('en', 'ru', 'et')) <> 3
                """, Long.class);

        assertThat(broken).isEmpty();
    }

    @Test
    void promptsAreUnambiguousInEachNativeLanguage() {
        for (String lang : List.of("ru", "et")) {
            List<String> duplicates = jdbc.queryForList("""
                    select f.normalized from word_form f
                    join concept c on c.id = f.concept_id and c.origin = 'SEED'
                    where f.lang = ? and f.is_primary
                    group by f.normalized having count(*) > 1
                    """, String.class, lang);

            assertThat(duplicates).as("duplicate %s prompts", lang).isEmpty();
        }
    }

    @Test
    void nativePromptNeverGivesTheEnglishAnswerAway() {
        Integer same = jdbc.queryForObject("""
                select count(*) from word_form n
                join word_form e on e.concept_id = n.concept_id and e.lang = 'en'
                where n.lang in ('ru', 'et') and n.is_primary and n.normalized = e.normalized
                """, Integer.class);

        assertThat(same).isZero();
    }

    @Test
    void formsAreNormalizedAndDifficultyIsInRange() {
        Integer notNormalized = jdbc.queryForObject(
                "select count(*) from word_form where normalized <> lower(text)", Integer.class);
        Integer badDifficulty = jdbc.queryForObject(
                "select count(*) from concept where difficulty not between 1 and 3", Integer.class);

        assertThat(notNormalized).isZero();
        assertThat(badDifficulty).isZero();
    }

    @Test
    void alternativeSpellingsAreAccepted() {
        List<String> answers = jdbc.queryForList("""
                select f.normalized from word_form f
                where f.lang = 'en' and f.concept_id = (
                    select concept_id from word_form where lang = 'en' and is_primary and normalized = 'grey')
                order by f.is_primary desc
                """, String.class);

        assertThat(answers).containsExactly("grey", "gray");
    }
}
