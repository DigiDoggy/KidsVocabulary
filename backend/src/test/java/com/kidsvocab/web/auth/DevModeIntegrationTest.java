package com.kidsvocab.web.auth;

import com.kidsvocab.IntegrationTest;
import com.kidsvocab.app.auth.DevMode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With DEV_MODE on, the app signs in to a fixed account without a password; its PIN still guards parent mode. */
@TestPropertySource(properties = "app.dev-mode=true")
class DevModeIntegrationTest extends IntegrationTest {

    @Test
    void signsInWithoutAPasswordAndKeepsThePin() throws Exception {
        mvc.perform(get("/api/v1/info")).andExpect(jsonPath("$.devMode").value(true));

        Session dev = session(mvc.perform(post("/api/v1/auth/dev-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PARENT")));
        mvc.perform(withAuth(get("/api/v1/account/me"), dev)).andExpect(jsonPath("$.email").value(DevMode.EMAIL));

        postJson("/api/v1/auth/login", Map.of("email", DevMode.EMAIL, "password", DevMode.PASSWORD))
                .andExpect(status().isOk());

        long childId = createChild(dev, "Mia");
        Session child = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + childId), dev)));
        mvc.perform(withAuth(post("/api/v1/auth/switch-to-parent"), child)
                        .contentType("application/json").content("{\"pin\":\"9999\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withAuth(post("/api/v1/auth/switch-to-parent"), child)
                        .contentType("application/json").content("{\"pin\":\"" + DevMode.PIN + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/dev-login")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/info")).andExpect(jsonPath("$.devMode").value(true));
    }
}
