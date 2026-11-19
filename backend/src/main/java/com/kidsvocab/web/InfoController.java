package com.kidsvocab.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service endpoint: application version and supported languages.
 * Used by the frontend to show that the backend and the database are reachable.
 */
@RestController
@RequestMapping("/api/v1/info")
public class InfoController {

    private final JdbcTemplate jdbc;
    private final ObjectProvider<BuildProperties> buildProperties;

    public InfoController(JdbcTemplate jdbc, ObjectProvider<BuildProperties> buildProperties) {
        this.jdbc = jdbc;
        this.buildProperties = buildProperties;
    }

    @GetMapping
    public InfoResponse info() {
        List<LanguageDto> languages = jdbc.query(
                "select code, name from language order by code",
                (rs, i) -> new LanguageDto(rs.getString("code"), rs.getString("name")));
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null ? build.getVersion() : "dev";
        return new InfoResponse("KidsVocabulary", version, languages);
    }

    public record InfoResponse(String name, String version, List<LanguageDto> languages) {
    }

    public record LanguageDto(String code, String name) {
    }
}
