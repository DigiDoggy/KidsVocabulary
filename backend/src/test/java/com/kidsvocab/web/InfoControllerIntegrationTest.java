package com.kidsvocab.web;

import com.kidsvocab.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public endpoints: Flyway migrations apply and the app reports itself without authentication. */
class InfoControllerIntegrationTest extends IntegrationTest {

    @Test
    void returnsLanguagesFromDatabase() throws Exception {
        mvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("KidsVocabulary"))
                .andExpect(jsonPath("$.languages.length()").value(3))
                .andExpect(jsonPath("$.languages[0].code").value("en"));
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
