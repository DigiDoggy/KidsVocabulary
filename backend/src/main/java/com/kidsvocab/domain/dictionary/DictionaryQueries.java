package com.kidsvocab.domain.dictionary;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-side dictionary queries that are awkward to express with JPA. */
@Repository
public class DictionaryQueries {

    /** {@code not in ()} is invalid SQL, so empty exclusion lists get a value that never matches. */
    private static final long NO_ID = -1L;
    private static final String NO_TEXT = "";

    private final NamedParameterJdbcTemplate jdbc;

    public DictionaryQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A concept as seen by a child with the given language pair.
     *
     * @param prompt          primary native form
     * @param primaryAnswer   primary target form
     * @param acceptedAnswers all normalised target forms, primary first
     */
    public record Card(long conceptId, String prompt, String primaryAnswer, List<String> acceptedAnswers) {
    }

    /** Criteria for picking new words from the shared dictionary. */
    public record SuggestionQuery(String nativeLang, String targetLang, int maxDifficulty, String topic, int limit,
                                  Collection<Long> excludeConcepts, Collection<String> excludePrompts,
                                  Collection<String> excludeAnswers) {
    }

    public Optional<Card> card(long conceptId, String nativeLang, String targetLang) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", conceptId).addValue("native", nativeLang).addValue("target", targetLang);
        List<String[]> head = jdbc.query("""
                select n.text as prompt, t.text as answer
                from word_form n
                join word_form t on t.concept_id = n.concept_id and t.lang = :target and t.is_primary
                where n.concept_id = :id and n.lang = :native and n.is_primary
                """, params, (rs, i) -> new String[]{rs.getString("prompt"), rs.getString("answer")});
        if (head.isEmpty()) {
            return Optional.empty();
        }
        List<String> answers = jdbc.queryForList("""
                select normalized from word_form
                where concept_id = :id and lang = :target
                order by is_primary desc, id
                """, params, String.class);
        return Optional.of(new Card(conceptId, head.getFirst()[0], head.getFirst()[1], answers));
    }

    /** Finds a concept whose primary native form and some target form match, shared or owned by the parent. */
    public Optional<Long> findPair(String nativeLang, String nativeNormalized, String targetLang,
                                   String targetNormalized, long parentId) {
        List<Long> ids = jdbc.queryForList("""
                select n.concept_id
                from word_form n
                join word_form t on t.concept_id = n.concept_id
                join concept c on c.id = n.concept_id
                where n.lang = :native and n.is_primary and n.normalized = :nn
                  and t.lang = :target and t.normalized = :tn
                  and (c.owner_parent_id is null or c.owner_parent_id = :parent)
                order by c.owner_parent_id nulls first, c.id
                limit 1
                """, new MapSqlParameterSource()
                .addValue("native", nativeLang).addValue("nn", nativeNormalized)
                .addValue("target", targetLang).addValue("tn", targetNormalized)
                .addValue("parent", parentId), Long.class);
        return ids.stream().findFirst();
    }

    /** Random shared concepts matching the query that have forms in both languages. */
    public List<Long> suggest(SuggestionQuery q) {
        return jdbc.queryForList("""
                select c.id
                from concept c
                join word_form n on n.concept_id = c.id and n.lang = :native and n.is_primary
                join word_form t on t.concept_id = c.id and t.lang = :target and t.is_primary
                where c.owner_parent_id is null
                  and c.difficulty <= :maxDifficulty
                  and (cast(:topic as varchar) is null or c.topic = :topic)
                  and c.id not in (:excludeConcepts)
                  and n.normalized not in (:excludePrompts)
                  and t.normalized not in (:excludeAnswers)
                order by random()
                limit :limit
                """, new MapSqlParameterSource()
                .addValue("native", q.nativeLang()).addValue("target", q.targetLang())
                .addValue("maxDifficulty", q.maxDifficulty()).addValue("topic", q.topic())
                .addValue("excludeConcepts", orSentinel(q.excludeConcepts(), NO_ID))
                .addValue("excludePrompts", orSentinel(q.excludePrompts(), NO_TEXT))
                .addValue("excludeAnswers", orSentinel(q.excludeAnswers(), NO_TEXT))
                .addValue("limit", q.limit()), Long.class);
    }

    /**
     * Concepts that must not be suggested to the child again: everything in its word history,
     * every word of a prepared or running week, and carried-over words still waiting for a week.
     */
    public Set<Long> conceptsToAvoid(long childId) {
        return Set.copyOf(jdbc.queryForList("""
                select concept_id from child_word where child_id = :child
                union
                select w.concept_id from week_word w join study_week s on s.id = w.week_id
                where s.child_id = :child and s.status in ('DRAFT', 'SCHEDULED', 'ACTIVE')
                union
                select concept_id from carry_over where child_id = :child and placed_week_id is null
                """, new MapSqlParameterSource("child", childId), Long.class));
    }

    /** True if a shared concept already uses this word as its primary form (a new one would be ambiguous). */
    public boolean sharedPrimaryExists(String lang, String normalized) {
        Integer count = jdbc.queryForObject("""
                select count(*) from word_form f join concept c on c.id = f.concept_id
                where c.owner_parent_id is null and f.lang = :lang and f.is_primary and f.normalized = :n
                """, new MapSqlParameterSource().addValue("lang", lang).addValue("n", normalized), Integer.class);
        return count != null && count > 0;
    }

    /** Primary target-language words of the given concepts. */
    public Set<String> primaryWords(Collection<Long> conceptIds, String lang) {
        if (conceptIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.queryForList("""
                select normalized from word_form where concept_id in (:ids) and lang = :lang and is_primary
                """, new MapSqlParameterSource().addValue("ids", conceptIds).addValue("lang", lang), String.class));
    }

    /** Concepts used by any week of the given month (to avoid repeating a word within a month). */
    public Set<Long> conceptsInMonth(long monthId) {
        return Set.copyOf(jdbc.queryForList("""
                select w.concept_id from week_word w join study_week s on s.id = w.week_id
                where s.month_id = :month
                """, new MapSqlParameterSource("month", monthId), Long.class));
    }

    private static <T> List<T> orSentinel(Collection<T> values, T sentinel) {
        List<T> list = new ArrayList<>(values);
        if (list.isEmpty()) {
            list.add(sentinel);
        }
        return list;
    }
}
