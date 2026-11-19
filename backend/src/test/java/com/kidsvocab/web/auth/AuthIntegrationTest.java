package com.kidsvocab.web.auth;

import com.kidsvocab.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registrationLogsTheParentIn() throws Exception {
        String email = uniqueEmail();

        String setCookie = postJson("/api/v1/auth/register",
                Map.of("email", "  " + email.toUpperCase() + " ", "password", PASSWORD, "pin", PIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("PARENT"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("refresh_token=", "HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");

        Session session = login(email, PASSWORD);
        mvc.perform(withAuth(get("/api/v1/account/me"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.timezone").value("Europe/Tallinn"))
                .andExpect(jsonPath("$.mode").value("PARENT"));
    }

    @Test
    void emailCanBeRegisteredOnlyOnce() throws Exception {
        String email = uniqueEmail();
        register(email);

        postJson("/api/v1/auth/register", Map.of("email", email.toUpperCase(), "password", PASSWORD, "pin", PIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        postJson("/api/v1/auth/register", Map.of("email", "not-an-email", "password", "short", "pin", "12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.pin").value("must be 4 to 6 digits"));

        postJson("/api/v1/auth/register",
                Map.of("email", uniqueEmail(), "password", PASSWORD, "pin", PIN, "timezone", "Mars/Olympus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void loginRejectsWrongCredentials() throws Exception {
        String email = uniqueEmail();
        register(email);

        postJson("/api/v1/auth/login", Map.of("email", email, "password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        postJson("/api/v1/auth/login", Map.of("email", uniqueEmail(), "password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsNeedAValidAccessToken() throws Exception {
        mvc.perform(get("/api/v1/account/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheTokenAndDetectsReuse() throws Exception {
        Session first = register();

        Session second = session(mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(first.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PARENT")));
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        mvc.perform(withAuth(get("/api/v1/account/me"), second)).andExpect(status().isOk());

        // the old token was used again: treat it as stolen and revoke the whole family
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(first.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(second.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshNeedsTheCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie("unknown")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        Session session = register();
        jdbc.update("update refresh_token set expires_at = now() - interval '1 minute' where token_hash = ?",
                com.kidsvocab.app.auth.TokenService.hash(session.refreshToken()));

        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Session session = register();

        String setCookie = mvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie(session.refreshToken())))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("Max-Age=0");
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(session.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void parentCanChangeThePin() throws Exception {
        Session session = register();

        mvc.perform(withAuth(put("/api/v1/account/pin"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "wrong", "newPin", "9999"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(withAuth(put("/api/v1/account/pin"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPin", "99"))))
                .andExpect(status().isBadRequest());
        mvc.perform(withAuth(put("/api/v1/account/pin"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPin", "9999"))))
                .andExpect(status().isNoContent());

        long childId = createChild(session, "Mia");
        Session child = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + childId), session)));
        postWithPin(child, PIN).andExpect(status().isForbidden());
        postWithPin(child, "9999").andExpect(status().isOk());
    }

    private Session login(String email, String password) throws Exception {
        return session(postJson("/api/v1/auth/login", Map.of("email", email, "password", password))
                .andExpect(status().isOk()));
    }

    private org.springframework.test.web.servlet.ResultActions postWithPin(Session session, String pin)
            throws Exception {
        return mvc.perform(withAuth(post("/api/v1/auth/switch-to-parent"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("pin", pin))));
    }
}
