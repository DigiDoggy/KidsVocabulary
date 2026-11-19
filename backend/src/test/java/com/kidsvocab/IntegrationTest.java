package com.kidsvocab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kidsvocab.web.auth.RefreshCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests against a real PostgreSQL. One container is shared by all test classes
 * (and the Spring context is cached), so every test creates its own accounts with unique e-mails.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse";
    protected static final String PIN = "2468";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;

    /** Access token plus the refresh token from the cookie. */
    protected record Session(String accessToken, String refreshToken) {
    }

    protected static String uniqueEmail() {
        return "parent-" + UUID.randomUUID() + "@example.com";
    }

    protected Session register() throws Exception {
        return register(uniqueEmail());
    }

    protected Session register(String email) throws Exception {
        return session(postJson("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD, "pin", PIN))
                .andExpect(status().isCreated()));
    }

    protected long createChild(Session parent, String name) throws Exception {
        String body = mvc.perform(withAuth(post("/api/v1/children"), parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(childRequest(name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    protected static Map<String, Object> childRequest(String name) {
        return Map.of("name", name, "level", "A1", "nativeLang", "ru", "targetLang", "en");
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected static MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder request, Session session) {
        return request.header("Authorization", "Bearer " + session.accessToken())
                .cookie(new Cookie(RefreshCookie.NAME, session.refreshToken()));
    }

    protected static Cookie refreshCookie(String value) {
        return new Cookie(RefreshCookie.NAME, value);
    }

    protected Session session(ResultActions result) throws Exception {
        MockHttpServletResponse response = result.andReturn().getResponse();
        JsonNode body = json.readTree(response.getContentAsString());
        Cookie cookie = response.getCookie(RefreshCookie.NAME);
        return new Session(body.get("accessToken").asText(), cookie == null ? null : cookie.getValue());
    }
}
