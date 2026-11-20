package com.kidsvocab.domain.study;

import com.kidsvocab.core.scoring.Outcome;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Per-child word history: what was studied and how it ended. Used to avoid repeating words. */
@Repository
public class ChildWordHistory {

    private final JdbcTemplate jdbc;

    public ChildWordHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(long childId, long conceptId, Outcome outcome, long weekId) {
        jdbc.update("""
                insert into child_word (child_id, concept_id, status, times_studied, last_week_id, updated_at)
                values (?, ?, ?, 1, ?, now())
                on conflict (child_id, concept_id) do update
                set status = excluded.status,
                    times_studied = child_word.times_studied + 1,
                    last_week_id = excluded.last_week_id,
                    updated_at = now()
                """, childId, conceptId, outcome.name(), weekId);
    }
}
