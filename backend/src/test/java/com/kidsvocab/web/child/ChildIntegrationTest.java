package com.kidsvocab.web.child;

import com.kidsvocab.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChildIntegrationTest extends IntegrationTest {

    @Test
    void parentManagesChildProfiles() throws Exception {
        Session parent = register();

        Map<String, Object> request = new HashMap<>(childRequest("  Mia "));
        request.put("birthDate", "2018-05-04");
        request.put("defaultWeekSize", 12);
        request.put("gender", "GIRL");
        long mia = readId(send(post("/api/v1/children"), parent, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gender").value("GIRL"))
                .andExpect(jsonPath("$.name").value("Mia"))
                .andExpect(jsonPath("$.birthDate").value("2018-05-04"))
                .andExpect(jsonPath("$.defaultWeekSize").value(12)));
        long leo = createChild(parent, "Leo");

        mvc.perform(withAuth(get("/api/v1/children"), parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("Mia", "Leo")));

        Map<String, Object> update = new HashMap<>(childRequest("Leo"));
        update.put("nativeLang", "et");
        update.put("level", "A2");
        send(put("/api/v1/children/" + leo), parent, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nativeLang").value("et"))
                .andExpect(jsonPath("$.level").value("A2"))
                .andExpect(jsonPath("$.gender").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.defaultWeekSize").value(10));
        Session miaSession = session(mvc.perform(withAuth(post("/api/v1/auth/switch-to-child/" + mia), parent)));
        mvc.perform(withAuth(get("/api/v1/me/home"), miaSession))
                .andExpect(jsonPath("$.child.gender").value("GIRL"));

        mvc.perform(withAuth(delete("/api/v1/children/" + mia), parent)).andExpect(status().isNoContent());
        mvc.perform(withAuth(get("/api/v1/children/" + mia), parent)).andExpect(status().isNotFound());
        mvc.perform(withAuth(get("/api/v1/children"), parent))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(leo));
    }

    @Test
    void parentsCannotSeeEachOthersChildren() throws Exception {
        Session owner = register();
        long mia = createChild(owner, "Mia");
        Session stranger = register();

        mvc.perform(withAuth(get("/api/v1/children/" + mia), stranger)).andExpect(status().isNotFound());
        send(put("/api/v1/children/" + mia), stranger, childRequest("Hacked")).andExpect(status().isNotFound());
        mvc.perform(withAuth(delete("/api/v1/children/" + mia), stranger)).andExpect(status().isNotFound());
        mvc.perform(withAuth(get("/api/v1/children"), stranger)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void profileIsValidated() throws Exception {
        Session parent = register();

        send(post("/api/v1/children"), parent, Map.of("name", " ", "nativeLang", "ru", "targetLang", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.level").exists());

        Map<String, Object> future = new HashMap<>(childRequest("Mia"));
        future.put("birthDate", LocalDate.now().plusDays(1).toString());
        future.put("defaultWeekSize", 0);
        send(post("/api/v1/children"), parent, future)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.birthDate").exists())
                .andExpect(jsonPath("$.errors.defaultWeekSize").exists());

        Map<String, Object> sameLanguage = new HashMap<>(childRequest("Mia"));
        sameLanguage.put("nativeLang", "en");
        send(post("/api/v1/children"), parent, sameLanguage)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        Map<String, Object> unknownLanguage = new HashMap<>(childRequest("Mia"));
        unknownLanguage.put("nativeLang", "fr");
        send(post("/api/v1/children"), parent, unknownLanguage)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unsupported language: fr"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Session session, Object body) throws Exception {
        return mvc.perform(withAuth(request, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private long readId(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
