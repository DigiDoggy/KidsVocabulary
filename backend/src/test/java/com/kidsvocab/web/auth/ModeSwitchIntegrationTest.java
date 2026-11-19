package com.kidsvocab.web.auth;

import com.kidsvocab.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModeSwitchIntegrationTest extends IntegrationTest {

    @Test
    void childModeSeesOnlyItsOwnProfile() throws Exception {
        Session parent = register();
        long mia = createChild(parent, "Mia");
        long leo = createChild(parent, "Leo");

        Session child = session(switchToChild(parent, mia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CHILD"))
                .andExpect(jsonPath("$.childId").value(mia)));

        mvc.perform(withAuth(get("/api/v1/account/me"), child))
                .andExpect(jsonPath("$.mode").value("CHILD"))
                .andExpect(jsonPath("$.childId").value(mia));
        mvc.perform(withAuth(get("/api/v1/children/" + mia), child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mia"));
        mvc.perform(withAuth(get("/api/v1/children/" + leo), child)).andExpect(status().isNotFound());
    }

    @Test
    void childModeCannotUseParentEndpoints() throws Exception {
        Session parent = register();
        long mia = createChild(parent, "Mia");
        Session child = session(switchToChild(parent, mia));

        mvc.perform(withAuth(get("/api/v1/children"), child)).andExpect(status().isForbidden());
        mvc.perform(withAuth(post("/api/v1/children"), child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(childRequest("Sneaky"))))
                .andExpect(status().isForbidden());
        mvc.perform(withAuth(put("/api/v1/account/pin"), child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPin", "0000"))))
                .andExpect(status().isForbidden());
        switchToChild(child, mia).andExpect(status().isForbidden());
    }

    @Test
    void refreshKeepsChildMode() throws Exception {
        Session parent = register();
        long mia = createChild(parent, "Mia");
        Session child = session(switchToChild(parent, mia));

        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(child.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CHILD"))
                .andExpect(jsonPath("$.childId").value(mia));
        // switching revoked the parent's refresh token
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(parent.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returningToParentModeNeedsThePin() throws Exception {
        Session parent = register();
        long mia = createChild(parent, "Mia");
        Session child = session(switchToChild(parent, mia));

        switchToParent(child, "1111")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));
        Session back = session(switchToParent(child, PIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PARENT")));

        mvc.perform(withAuth(get("/api/v1/children"), back))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void tooManyWrongPinsLockParentMode() throws Exception {
        Session parent = register();
        Session child = session(switchToChild(parent, createChild(parent, "Mia")));

        for (int i = 0; i < 5; i++) {
            switchToParent(child, "0000").andExpect(status().isForbidden());
        }
        switchToParent(child, PIN)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));
    }

    @Test
    void cannotOpenAnotherParentsChild() throws Exception {
        Session owner = register();
        long mia = createChild(owner, "Mia");
        Session stranger = register();

        switchToChild(stranger, mia).andExpect(status().isNotFound());
    }

    private ResultActions switchToChild(Session session, long childId) throws Exception {
        return mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + childId), session));
    }

    private ResultActions switchToParent(Session session, String pin) throws Exception {
        return mvc.perform(withAuth(post("/api/v1/auth/switch-to-parent"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("pin", pin))));
    }
}
