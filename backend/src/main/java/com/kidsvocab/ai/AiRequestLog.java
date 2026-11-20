package com.kidsvocab.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** One row per provider call, for monitoring quality and quota (no prompts or personal data). */
@Repository
public class AiRequestLog {

    public enum Status { OK, INVALID, ERROR, RATE_LIMIT }

    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbc;

    public AiRequestLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String provider, Status status, Integer wordsIn, Integer wordsValid, long latencyMs,
                       String error) {
        String trimmed = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        jdbc.update("""
                insert into ai_request_log (provider, status, words_in, words_valid, latency_ms, error)
                values (?, ?, ?, ?, ?, ?)
                """, provider, status.name(), wordsIn, wordsValid, (int) latencyMs, trimmed);
    }
}
