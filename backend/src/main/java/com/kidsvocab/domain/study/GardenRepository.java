package com.kidsvocab.domain.study;

import com.kidsvocab.core.garden.GrowthStage;
import com.kidsvocab.core.garden.Plant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The child's word garden: the plant columns of {@code child_word}, each shown with the latest frozen
 * week word of its concept, and the word lists of review sessions.
 */
@Repository
public class GardenRepository {

    /**
     * A plant with its word.
     *
     * @param weekWordId the latest frozen week word of the concept (texts, answers, picture)
     * @param monthSeq   number of the study month the word was planted in ({@code null} if unknown)
     */
    public record GardenPlant(long conceptId, Plant plant, long weekWordId, String prompt, String answer,
                              String emoji, Integer monthSeq) {
    }

    private static final String SELECT = """
            select cw.concept_id, cw.plant_kind, cw.growth_stage, cw.review_interval_weeks, cw.next_review_on,
                   ww.id as week_word_id, ww.prompt_text, ww.primary_answer, ww.emoji, m.seq_no
            from child_word cw
            join week_word ww on ww.week_id = cw.last_week_id and ww.concept_id = cw.concept_id
            left join study_week pw on pw.id = cw.planted_week_id
            left join study_month m on m.id = pw.month_id
            where cw.child_id = ? and cw.plant_kind is not null
            """;

    private final JdbcTemplate jdbc;

    public GardenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Plants a word learned in {@code weekId}, unless it already grows in the garden. */
    public void plantIfNew(long childId, long conceptId, Plant plant, long weekId) {
        jdbc.update("""
                update child_word
                set plant_kind = ?, growth_stage = ?, review_interval_weeks = ?, next_review_on = ?,
                    planted_week_id = ?
                where child_id = ? and concept_id = ? and plant_kind is null
                """, plant.kind(), plant.stage().ordinal(), plant.intervalWeeks(), Date.valueOf(plant.nextReviewOn()),
                weekId, childId, conceptId);
    }

    /**
     * Every plant of the child, oldest beds first. Within a bed plants of one kind stand together,
     * youngest first, so a row never looks like one kind turning into another.
     */
    public List<GardenPlant> plants(long childId) {
        return jdbc.query(SELECT + " order by m.seq_no nulls last, cw.plant_kind, cw.growth_stage, cw.concept_id",
                GardenRepository::map, childId);
    }

    /** Plants that want water on {@code today}, the most overdue first. */
    public List<GardenPlant> due(long childId, LocalDate today, int limit) {
        return jdbc.query(SELECT + " and cw.next_review_on <= ? order by cw.next_review_on, cw.concept_id limit ?",
                GardenRepository::map, childId, Date.valueOf(today), limit);
    }

    public int countDue(long childId, LocalDate today) {
        Integer n = jdbc.queryForObject("""
                select count(*) from child_word
                where child_id = ? and plant_kind is not null and next_review_on <= ?
                """, Integer.class, childId, Date.valueOf(today));
        return n == null ? 0 : n;
    }

    /** The plants of the given concepts, if they still grow. */
    public List<GardenPlant> plantsOf(long childId, List<Long> conceptIds) {
        if (conceptIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SELECT + " and cw.concept_id = any (?)", GardenRepository::map, childId,
                conceptIds.toArray(Long[]::new));
    }

    public void update(long childId, long conceptId, Plant plant) {
        jdbc.update("""
                update child_word
                set growth_stage = ?, review_interval_weeks = ?, next_review_on = ?, updated_at = now()
                where child_id = ? and concept_id = ?
                """, plant.stage().ordinal(), plant.intervalWeeks(), Date.valueOf(plant.nextReviewOn()),
                childId, conceptId);
    }

    public void addReviewWords(long sessionId, List<GardenPlant> plants) {
        jdbc.batchUpdate("insert into review_word (session_id, week_word_id, concept_id) values (?, ?, ?)",
                plants, plants.size(), (ps, p) -> {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, p.weekWordId());
                    ps.setLong(3, p.conceptId());
                });
    }

    /** The words of a review session: week word id to concept id, ordered by week word id. */
    public Map<Long, Long> reviewWords(long sessionId) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query("select week_word_id, concept_id from review_word where session_id = ? order by week_word_id",
                rs -> {
                    result.put(rs.getLong(1), rs.getLong(2));
                }, sessionId);
        return result;
    }

    private static GardenPlant map(ResultSet rs, int row) throws SQLException {
        Plant plant = new Plant(rs.getInt("plant_kind"), GrowthStage.ofCode(rs.getInt("growth_stage")),
                rs.getInt("review_interval_weeks"), rs.getDate("next_review_on").toLocalDate());
        int seq = rs.getInt("seq_no");
        Integer monthSeq = rs.wasNull() ? null : seq;   // read before any other column
        return new GardenPlant(rs.getLong("concept_id"), plant, rs.getLong("week_word_id"),
                rs.getString("prompt_text"), rs.getString("primary_answer"), rs.getString("emoji"),
                monthSeq);
    }
}
